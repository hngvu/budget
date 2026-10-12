package so.budget.api.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AccountTypeConverter implements AttributeConverter<AccountType, String> {

    @Override
    public String convertToDatabaseColumn(AccountType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public AccountType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AccountType.fromValue(dbData);
    }
}
