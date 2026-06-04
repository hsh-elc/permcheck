package de.hsh.permcheck.internal;
import java.lang.reflect.Executable;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

public class PermitPropertyCheck extends BasicPermitCheck {

    public PermitPropertyCheck() {
        super("property", "deny.propertyExceptSpecifiedPermissions", "permit.property");
    }

    @Override
    protected void registerImpl(Map<Executable, Insert> registry) throws Exception {
        registry.put(System.class.getDeclaredMethod("getProperty", String.class), firstArg(Action.READ));
        registry.put(System.class.getDeclaredMethod("getProperty", String.class, String.class), firstArg(Action.READ));
        registry.put(System.class.getDeclaredMethod("getProperties"), any(Action.READ, Action.WRITE));

        registry.put(System.class.getDeclaredMethod("setProperties", Properties.class), any(Action.READ, Action.WRITE));
        registry.put(System.class.getDeclaredMethod("setProperty", String.class, String.class), firstArg(Action.WRITE));
        registry.put(System.class.getDeclaredMethod("clearProperty", String.class), firstArg(Action.WRITE));

        // user.timezone:
        registry.put(TimeZone.class.getDeclaredMethod("setDefault", TimeZone.class), timeZoneSetDefault());

        // user.language:
        registry.put(Locale.class.getDeclaredMethod("setDefault", Locale.class), localeSetDefault());
        registry.put(Locale.class.getDeclaredMethod("setDefault", Locale.Category.class, Locale.class), localeSetDefault());
    }


    private class TimeZoneSetDefaultInsert extends Insert {
        @Override
        public void onEnterImpl(Hook hook) {
            checkWrite("user.timezone", hook);
        }
    }

    public TimeZoneSetDefaultInsert timeZoneSetDefault() {
        return new TimeZoneSetDefaultInsert();
    }


    private class LocaleSetDefaultInsert extends Insert {
        @Override
        public void onEnterImpl(Hook hook) {
            checkWrite("user.language", hook);
        }
    }

    public LocaleSetDefaultInsert localeSetDefault() {
        return new LocaleSetDefaultInsert();
    }
    
}
