package smartpot.com.api.Users.Validation;

public class UserRegex {
    public static final String NAME_PATTERN = "^[a-zA-ZÁ-ÿá-ÿ]{4,15}$";
    public static final String LASTNAME_PATTERN = "^[a-zA-ZÁ-ÿá-ÿ]{4,30}$";
    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
}
