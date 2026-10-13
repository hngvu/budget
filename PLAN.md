# BudgetFlow API Specification & Implementation Plan

> Tài liệu đặc tả kỹ thuật chi tiết các RESTful API endpoints, kiến trúc luồng dữ liệu (Data Flow), mô hình phân quyền và cách thức xử lý nghiệp vụ tại tầng Service cho hệ thống BudgetFlow (YNAB-style Zero-based Budgeting).

---

## 1. Nguyên Tắc & Quy Ước Kiến Trúc

### 1.1. Đơn vị tiền tệ (Milliunits)
* Mọi giá trị tiền tệ trong Request, Response và Database đều là số nguyên `BIGINT` dạng **milliunits**:
  * `1000 milliunits = 1 đơn vị tiền tệ` (Ví dụ: `50,000 VND` -> `50,000,000 milliunits`, `$10.50` -> `10,500 milliunits`).
* Quy ước chiều dòng tiền (Transactions):
  * **Inflow (Thu vào)**: Số dương (`amount > 0`).
  * **Outflow (Chi ra)**: Số âm (`amount < 0`).
  * `amount != 0` (Bắt buộc không được bằng 0).

### 1.2. Quy ước tháng (Month Convention)
* Cột `month` luôn chuẩn hóa về ngày đầu tiên của tháng theo định dạng `yyyy-MM-01` (Ví dụ: `2026-10-01`).

### 1.3. Multi-Tenancy & Bảo mật (Tenant Isolation)
* Mọi API sau `/api/v1/plans/{planId}/**` đều phải thực hiện kiểm tra quyền sở hữu (Ownership Verification) tại Service hoặc Aspect:
  `plan.user.id == currentUser.id`.
* Mọi câu truy vấn Repository đều bắt buộc mang theo `plan_id` để đảm bảo tuyệt đối không rò rỉ dữ liệu chéo giữa các Plan.

### 1.4. Phân chia trách nhiệm (Service vs Database)
* **Database Functions/Views**:
  * `v_account_balances`: View tính số dư tức thời theo tài khoản.
  * `fn_category_months(plan_id, until)`: Hàm đệ quy tính số tiền khả dụng lũy kế (`available`) và chi tiêu (`activity`) từng danh mục.
  * `fn_month_summary(plan_id, until)`: Hàm tổng hợp dòng tiền tháng (`income`, `assigned`, `activity`, `overspent`, `ready_to_assign`).
* **Service Layer**:
  * Điều phối giao dịch phân tán (Transfer 2 đầu, gộp Payee, Move money).
  * Kiểm tra nghiệp vụ (Số dư tài khoản khi đóng, kiểm tra target, tính toán Underfunded / Auto-Assign).
  * Xử lý xác thực, cấp token, mã hóa mật khẩu, kiểm tra giới hạn gói thuê bao (FREE vs PRO).

---

## 2. Chuẩn Định Dạng Dữ Liệu & Xử Lý Lỗi

### 2.1. Phản hồi thành công (Standard Success Envelope)
```json
{
  "success": true,
  "data": { ... },
  "message": "Thao tác thành công",
  "timestamp": "2026-10-13T21:40:00Z"
}
```

### 2.2. Phản hồi lỗi chuẩn RFC 7807 (ProblemDetail)
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Số dư tài khoản chưa bằng 0, không thể đóng tài khoản",
  "instance": "/api/v1/plans/123/accounts/456/close",
  "timestamp": "2026-10-13T21:40:00Z",
  "errors": [
    {
      "field": "amount",
      "message": "Số tiền không được bằng 0"
    }
  ]
}
```

---

## 3. Chi Tiết Danh Sách Endpoints & Cách Xử Lý

```
┌────────────────────────────────────────────────────────────────────────┐
│                        TỔNG QUAN HỆ THỐNG MODULE                       │
├─────────────────────┬──────────────────────────────────────────────────┤
│ Module 1: Auth      │ /api/v1/auth/**, /api/v1/users/**                │
│ Module 2: Plans     │ /api/v1/plans/**                                 │
│ Module 3: Accounts  │ /api/v1/plans/{planId}/accounts/**               │
│ Module 4: Categories│ /api/v1/plans/{planId}/category-groups/**        │
│                     │ /api/v1/plans/{planId}/categories/**             │
│ Module 5: Budgeting │ /api/v1/plans/{planId}/months/**                 │
│ Module 6: Txns      │ /api/v1/plans/{planId}/transactions/**           │
│ Module 7: Payees    │ /api/v1/plans/{planId}/payees/**                 │
└─────────────────────┴──────────────────────────────────────────────────┘
```

---

### Module 1: Authentication & User Profile

#### 1.1 `POST /api/v1/auth/register`
* **Mục đích:** Đăng ký tài khoản người dùng mới.
* **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "Password123@",
    "displayName": "Nguyễn Văn A"
  }
  ```
* **Xử lý tại Service (`AuthService.register`):**
  1. Kiểm tra `email` đã tồn tại trong `UserRepository` chưa. Nếu có -> quăng `DuplicateResourceException`.
  2. Băm mật khẩu bằng `PasswordEncoder` (BCrypt).
  3. Lưu `User` mới với `subscriptionTier = FREE`.
  4. Tự động sinh `Plan` mặc định đầu tiên (tên "Ngân sách cá nhân", tiền tệ `VND`, 0 số thập phân).
  5. Sinh cặp JWT Access Token (hạn 24h) và Refresh Token (hạn 7 ngày). Trả về token kèm thông tin user.

#### 1.2 `POST /api/v1/auth/login`
* **Mục đích:** Đăng nhập hệ thống bằng email và mật khẩu.
* **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "Password123@"
  }
  ```
* **Xử lý tại Service (`AuthService.login`):**
  1. Tìm `User` theo email. Nếu không có -> trả lỗi `401 Bad credentials`.
  2. Kiểm tra `passwordEncoder.matches(rawPassword, user.getPasswordHash())`.
  3. Tạo JWT Claims chứa `userId`, `email`, `subscriptionTier`.
  4. Trả về Access Token + Refresh Token + User profile.

#### 1.3 `POST /api/v1/auth/refresh`
* **Mục đích:** Đổi Access Token mới từ Refresh Token hợp lệ.
* **Request Body:** `{ "refreshToken": "string" }`
* **Xử lý tại Service:** Xác thực tính toàn vẹn của token, kiểm tra hạn dùng, sinh Access Token mới.

#### 1.4 `GET /api/v1/users/me`
* **Mục đích:** Lấy thông tin user hiện tại từ Security Context.
* **Xử lý:** Lấy `userId` từ token -> nạp thông tin user (loại bỏ `passwordHash`).

---

### Module 2: Plans (Quản Lý Kế Hoạch Ngân Sách)

#### 2.1 `GET /api/v1/plans`
* **Mục đích:** Lấy danh sách tất cả các ngân sách thuộc quyền sở hữu của user hiện tại.
* **Xử lý:** Query `PlanRepository.findAllByUserId(currentUserId)`.

#### 2.2 `POST /api/v1/plans`
* **Mục đích:** Tạo một ngân sách mới.
* **Request Body:**
  ```json
  {
    "name": "Ngân Sách Gia Đình 2026",
    "currencyCode": "VND",
    "decimalDigits": 0
  }
  ```
* **Xử lý tại Service (`PlanService.createPlan`):**
  1. Kiểm tra `subscriptionTier`: Nếu user là `FREE` và đã sở hữu `>= 1` Plan -> ném `ForbiddenException` ("Tài khoản FREE chỉ được tạo tối đa 1 Plan. Hãy nâng cấp PRO.").
  2. Lưu bản ghi `Plan`.
  3. Database trigger `trg_plan_defaults` sẽ **tự động** tạo:
     - Nhóm danh mục `Internal Master Category` (`hidden = true`).
     - Danh mục hệ thống `Inflow: Ready to Assign` (`system_kind = 'INFLOW_READY_TO_ASSIGN'`).
  4. Service khởi tạo thêm các nhóm danh mục mẫu thông dụng (Tiền nhà & Hóa đơn, Ăn uống, Tiết kiệm) để người dùng dễ bắt đầu.

#### 2.3 `GET /api/v1/plans/{planId}`
* **Xử lý:** Kiểm tra quyền sở hữu plan -> trả về thông tin chi tiết cấu hình.

#### 2.4 `PUT /api/v1/plans/{planId}`
* **Mục đích:** Đổi tên hoặc cập nhật định dạng hiển thị tiền tệ.

#### 2.5 `DELETE /api/v1/plans/{planId}`
* **Mục đích:** Xóa hoàn toàn một ngân sách.
* **Xử lý:** Kiểm tra quyền sở hữu -> Gọi `delete`. Database đã cài đặt `ON DELETE CASCADE` trên tất cả các bảng liên kết nên toàn bộ account, category, transaction thuộc plan sẽ được dọn dẹp sạch sẽ.

---

### Module 3: Accounts (Tài Khoản Tài Chính)

#### 3.1 `GET /api/v1/plans/{planId}/accounts`
* **Mục đích:** Danh sách tài khoản kèm số dư tính toán thời gian thực.
* **Xử lý tại Service:**
  1. Kiểm tra quyền sở hữu `planId`.
  2. Nạp dữ liệu từ View `v_account_balances` (kết hợp bảng `accounts` và view tính tổng `balance`, `cleared_balance`).
  3. Sắp xếp theo `sort_order ASC`.
* **Response:**
  ```json
  [
    {
      "id": "uuid",
      "name": "Techcombank Checking",
      "type": "CHECKING",
      "closed": false,
      "balance": 15000000000,
      "clearedBalance": 15000000000,
      "sortOrder": 0
    }
  ]
  ```

#### 3.2 `POST /api/v1/plans/{planId}/accounts`
* **Mục đích:** Tạo tài khoản mới. Hỗ trợ nhập số dư ban đầu (Starting Balance).
* **Request Body:**
  ```json
  {
    "name": "VPBank Tiết Kiệm",
    "type": "SAVINGS",
    "initialBalance": 10000000000,
    "initialBalanceDate": "2026-10-01",
    "note": "Tài khoản tích lũy khẩn cấp"
  }
  ```
* **Xử lý tại Service (`AccountService.createAccount`):**
  1. Validate tên không để trống, `type` hợp lệ (`CHECKING`, `SAVINGS`, `CASH`, `CREDIT_CARD`).
  2. Tạo bản ghi `Account`.
  3. **Nếu `initialBalance != 0`:**
     - Tự động tạo một Payee có tên "Starting Balance".
     - Lấy danh mục hệ thống `Inflow: Ready to Assign` của plan.
     - Tạo 1 bản ghi `Transaction` đầu tiên: `amount = initialBalance`, `cleared = true`, `date = initialBalanceDate`, `categoryId = readyToAssign.id`.
     - Số tiền này sẽ tự động chảy vào quỹ "Ready to Assign" của tháng tương ứng.

#### 3.3 `PUT /api/v1/plans/{planId}/accounts/{accountId}/close`
* **Mục đích:** Đóng tài khoản (khi không còn sử dụng).
* **Xử lý tại Service:**
  1. Kiểm tra số dư từ View: Nếu `balance != 0` -> từ chối (`400 Bad Request: "Số dư tài khoản phải bằng 0 trước khi đóng. Vui lòng chuyển tiền sang tài khoản khác"`).
  2. Đặt `closed = true`.

#### 3.4 `DELETE /api/v1/plans/{planId}/accounts/{accountId}`
* **Xử lý:** Chỉ cho phép xóa cứng nếu tài khoản chưa có giao dịch phát sinh nào (hoặc chỉ có duy nhất giao dịch mở đầu). Nếu đã có lịch sử giao dịch -> bắt buộc dùng thao tác `close` để bảo toàn lịch sử sổ sách.

---

### Module 4: Categories & Groups (Nhóm & Danh Mục Ngân Sách)

#### 4.1 `GET /api/v1/plans/{planId}/category-groups`
* **Mục đích:** Lấy toàn bộ cây danh mục phân cấp (Groups -> Categories) kèm cấu hình Target.
* **Xử lý:** Lọc bỏ nhóm nội bộ `system_kind IS NOT NULL`, trả về cây JSON đã gom nhóm.

#### 4.2 `POST /api/v1/plans/{planId}/category-groups`
* **Request Body:** `{ "name": "Chi Phí Sinh Hoạt Cố Định" }`
* **Xử lý:** Tính `sortOrder = max(sortOrder) + 1` và lưu nhóm mới.

#### 4.3 `POST /api/v1/plans/{planId}/category-groups/{groupId}/categories`
* **Mục đích:** Tạo một danh mục con trong nhóm.
* **Request Body:**
  ```json
  {
    "name": "Tiền Điện & Nước",
    "note": "Đóng trước ngày 15 hàng tháng",
    "target": {
      "targetType": "NEED",
      "targetAmount": 1500000000,
      "targetCadence": 1,
      "targetDay": 15,
      "targetNeedsWholeAmount": true
    }
  }
  ```

#### 4.4 `PUT /api/v1/plans/{planId}/categories/{categoryId}/target`
* **Mục đích:** Cài đặt hoặc cập nhật mục tiêu tài chính (YNAB Target) cho danh mục.
* **Các loại Target hỗ trợ:**
  * `NEED` (Need for spending): Cần chi tiêu định kỳ (hàng tuần/hàng tháng).
  * `TB` (Target Balance): Tiết kiệm cho đến khi đủ số tiền (không hạn ngày).
  * `TBD` (Target Balance by Date): Tiết kiệm đủ tiền trước ngày `targetDate`.
  * `MF` (Monthly Funding): Mỗi tháng cố định bỏ vào một khoản bất kể đã tiêu bao nhiêu.
  * `DEBT`: Trả nợ dần.

---

### Module 5: Budget Month & Allocations (Trọng Tâm Lập Ngân Sách)

#### 5.1 `GET /api/v1/plans/{planId}/months/{month}`
* **Mục đích:** Lấy toàn bộ bảng phân bổ ngân sách của một tháng cụ thể (`month = yyyy-MM-01`).
* **Xử lý tại Service (`BudgetService.getMonthDetails`):**
  1. Gọi database function `fn_month_summary(plan_id, month)`:
     - Nhận về: `income`, `assigned`, `activity`, `overspent`, `ready_to_assign`.
  2. Gọi database function `fn_category_months(plan_id, month)`:
     - Nhận về danh sách từng danh mục: `assigned` (đã phân bổ tháng này), `activity` (tổng chi tiêu tháng này), `available` (số dư khả dụng lũy kế).
  3. **Tính toán Target Status tại Service:**
     - Duyệt từng category có target:
       - Tính `underfunded = max(0, targetNeededThisMonth - assigned)`.
       - Tính `goalProgressPercentage = (available / targetAmount) * 100`.
  4. Trả về payload hoàn chỉnh cho giao diện bảng tính ngân sách.

#### 5.2 `PATCH /api/v1/plans/{planId}/months/{month}/categories/{categoryId}`
* **Mục đích:** Phân bổ/gán tiền vào một danh mục trong tháng (`Assigned`).
* **Request Body:** `{ "assigned": 2000000000 }` (Phân bổ 2,000,000 VND)
* **Xử lý tại Service (`BudgetService.assignMoney`):**
  1. Kiểm tra composite key: `MonthCategoryAssignmentId(categoryId, month)`.
  2. Dùng cơ chế Upsert: Nếu đã có record -> cập nhật cột `assigned`, nếu chưa có -> tạo mới `MonthCategoryAssignment`.
  3. Số tiền `Ready to Assign` của tháng sẽ tự động được điều chỉnh khi gọi lại `fn_month_summary`.

#### 5.3 `POST /api/v1/plans/{planId}/months/{month}/move-money`
* **Mục đích:** Chuyển tiền từ danh mục này sang danh mục khác (Bù tiền danh mục bị âm / Cover overspending).
* **Request Body:**
  ```json
  {
    "fromCategoryId": "uuid-1",
    "toCategoryId": "uuid-2",
    "amount": 500000000
  }
  ```
* **Xử lý tại Service (Chạy trong 1 `@Transactional`):**
  1. Kiểm tra `amount > 0`.
  2. Trừ `assigned` của `fromCategoryId` đi `amount`.
  3. Cộng `assigned` của `toCategoryId` thêm `amount`.
  4. Lưu đồng thời 2 bản ghi. Đảm bảo tổng `Ready to Assign` không đổi.

#### 5.4 `POST /api/v1/plans/{planId}/months/{month}/auto-assign`
* **Mục đích:** Tự động phân bổ ngân sách (Tính năng Quick Budget).
* **Request Body:**
  ```json
  {
    "mode": "UNDERFUNDED" // Hoặc "ASSIGNED_LAST_MONTH", "AVERAGE_SPENT"
  }
  ```
* **Xử lý tại Service:**
  1. Lấy số dư `readyToAssign` hiện tại của tháng từ `fn_month_summary`.
  2. Duyệt các danh mục theo độ ưu tiên:
     - Nếu `mode == "UNDERFUNDED"`: Tính số tiền còn thiếu cho từng target.
     - Phân bổ lần lượt cho đến khi hết `readyToAssign` hoặc đáp ứng toàn bộ mục tiêu.
  3. Batch update danh sách `MonthCategoryAssignment`.

---

### Module 6: Transactions (Giao Dịch Thu / Chi / Chuyển Khoản)

#### 6.1 `GET /api/v1/plans/{planId}/transactions`
* **Mục đích:** Tìm kiếm và phân trang danh sách giao dịch.
* **Query Params:** `accountId`, `categoryId`, `payeeId`, `fromDate`, `toDate`, `cleared`, `page`, `size`, `sort`.
* **Xử lý:** Sử dụng Spring Data JPA `Specification` hoặc QueryDSL để lọc linh hoạt theo điều kiện.

#### 6.2 `POST /api/v1/plans/{planId}/transactions`
* **Mục đích:** Tạo giao dịch mới (hỗ trợ cả giao dịch thường và Chuyển khoản liên tài khoản).
* **Trường hợp A: Giao dịch chi tiêu thông thường:**
  ```json
  {
    "accountId": "uuid-acc",
    "date": "2026-10-13",
    "amount": -50000000,
    "payeeName": "Circle K",
    "categoryId": "uuid-cat",
    "memo": "Mua cafe",
    "cleared": true
  }
  ```
  * **Xử lý:**
    1. Tìm hoặc tự động tạo mới `Payee` nếu `payeeName` chưa có trong plan.
    2. Kiểm tra `categoryId` thuộc đúng `planId`.
    3. Lưu bản ghi `Transaction`.

* **Trường hợp B: Giao dịch chuyển khoản (Transfer between accounts):**
  ```json
  {
    "accountId": "uuid-acc-1",
    "transferAccountId": "uuid-acc-2",
    "date": "2026-10-13",
    "amount": -1000000000,
    "memo": "Chuyển tiền vào tài khoản tiết kiệm",
    "cleared": true
  }
  ```
  * **Xử lý tại Service (`TransactionService.createTransfer` - Chạy trong `@Transactional`):**
    1. Kiểm tra `accountId != transferAccountId`. Cả 2 tài khoản phải thuộc cùng `planId` và không bị đóng (`closed == false`).
    2. Ràng buộc YNAB: Transfer giữa 2 tài khoản on-budget **không có category** (`categoryId = null`).
    3. Tạo `Transaction A` (Đầu chuyển đi): `amount = -1000000000`.
    4. Tạo `Transaction B` (Đầu nhận về): `amount = +1000000000`.
    5. Gán ID chéo:
       - `A.transferAccountId = B.accountId`, `A.transferTransactionId = B.id`
       - `B.transferAccountId = A.accountId`, `B.transferTransactionId = A.id`
    6. Lưu cả 2 bản ghi.
    7. Database trigger `trg_transfer` sẽ kiểm tra tính cân xứng đối ứng của 2 dòng tại thời điểm commit.

#### 6.3 `PUT /api/v1/plans/{planId}/transactions/{id}`
* **Xử lý:**
  * Nếu là giao dịch đơn: Cập nhật thông thường.
  * Nếu là giao dịch chuyển khoản: Tự động tìm giao dịch đối ứng qua `transferTransactionId`, cập nhật ngày tháng và số tiền đối xứng (`-amount`) cho cả hai đầu.

#### 6.4 `DELETE /api/v1/plans/{planId}/transactions/{id}`
* **Xử lý:**
  * Nếu là transfer: Tự động xóa cả giao dịch đối xứng trong cùng một transaction.

#### 6.5 `POST /api/v1/plans/{planId}/transactions/import`
* **Mục đích:** Import danh sách giao dịch từ file sao kê CSV/Excel.
* **Xử lý:**
  * Bóc tách dữ liệu từng dòng, tạo mã băm duy nhất cho `importId` (dựa trên `date + amount + payee + fitId`).
  * Bỏ qua các giao dịch đã tồn tại `import_id` trong tài khoản đó (Deduplication).

---

### Module 7: Payees (Quản Lý Đối Tượng Giao Dịch)

#### 7.1 `GET /api/v1/plans/{planId}/payees`
* **Xử lý:** Lấy danh sách đối tượng nhận/chuyển tiền trong plan.

#### 7.2 `PUT /api/v1/plans/{planId}/payees/{id}`
* **Xử lý:** Đổi tên hiển thị của Payee (sẽ tự động phản ánh trên tất cả các giao dịch cũ).

#### 7.3 `POST /api/v1/plans/{planId}/payees/merge`
* **Mục đích:** Gộp nhiều Payee bị trùng (Ví dụ: "Circle K Hai Ba Trung", "Circle K Quan 1" -> "Circle K").
* **Request Body:**
  ```json
  {
    "targetPayeeId": "uuid-chinh",
    "sourcePayeeIds": ["uuid-phu-1", "uuid-phu-2"]
  }
  ```
* **Xử lý:**
  1. Update toàn bộ `transactions` có `payee_id` thuộc `sourcePayeeIds` sang `targetPayeeId`.
  2. Xóa các `sourcePayeeIds` khỏi bảng `payees`.

---

## 4. Kế Hoạch Triển Khai Chi Tiết (Implementation Roadmap)

| Giai đoạn | Nội dung công việc | Output cụ thể |
| :--- | :--- | :--- |
| **P1: Repositories & Projections** | Viết toàn bộ interface Spring Data JPA kế thừa `JpaRepository`, định nghĩa native queries cho Stored Functions và Views | `UserRepository`, `PlanRepository`, `AccountRepository`, `TransactionRepository`, `MonthSummaryProjection`,... |
| **P2: DTOs & Global Exceptions** | Xây dựng record DTOs với validation annotations (`@NotNull`, `@NotBlank`), tạo `@RestControllerAdvice` format chuẩn RFC 7807 | `ApiResponse<T>`, `ProblemDetail`, `CreateAccountRequest`, `AssignMoneyRequest`,... |
| **P3: Security & JWT Module** | Cấu hình Spring Security 6 / Spring Boot 4, JWT Filter, Service xác thực đăng nhập/đăng ký | `SecurityConfig`, `JwtProvider`, `AuthService`, `AuthController` |
| **P4: Core Account & Plan Services** | Dựng CRUD cho Plan, Account (kèm Starting Balance logic), Category (kèm Target config) | `PlanService`, `AccountService`, `CategoryService` |
| **P5: Transaction & Transfer Engine** | Triển khai logic ghi giao dịch thu/chi, xử lý logic chuyển khoản 2 đầu kèm trigger verification | `TransactionService`, `TransactionController` |
| **P6: Budgeting Engine (YNAB Core)** | Kết nối DB Functions tính `available`, `ready_to_assign`, triển khai tính năng Move Money, Auto-assign | `BudgetService`, `BudgetMonthController` |
| **P7: Integration Tests & Swagger** | Viết Unit Test, Integration Test với Testcontainers PostgreSQL, hoàn thiện Swagger UI | `@SpringBootTest`, OpenAPI documentation |
