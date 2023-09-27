package org.telegram.ui.DialogBuilder;

import android.content.Context;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.view.View;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.Components.EditTextCaption;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;

public class PhoneEditTemplate extends EditTemplate {
    static private HashMap<String, String> countriesMap = new HashMap<>();
    static private HashMap<String, String> codesMap = new HashMap<>();
    static private HashMap<String, String> phoneFormatMap = new HashMap<>();
    static private HashMap<String, String> languageMap = new HashMap<>();

    @Override
    public View create(Context context) {
        EditTextCaption editText = (EditTextCaption)super.create(context);
        editText.setInputType(InputType.TYPE_CLASS_PHONE);
        editText.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
        editText.setText(text);

        String hint = getPhoneNumberHint();
        if (hint != null) {
            editText.setHint(hint);
        }
        return editText;
    }

    private String getPhoneNumberHint() {
        loadCountryCodes();
        TelephonyManager manager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (manager == null) {
            return null;
        }
        String fullCountryName = languageMap.get(manager.getSimCountryIso().toUpperCase());
        if (fullCountryName == null) {
            return null;
        }
        String countryCode = countriesMap.get(fullCountryName);
        String format = phoneFormatMap.get(countryCode);
        if (format != null) {
            return "+" + countryCode + " " + format.replace('X', '–');
        } else {
            return null;
        }
    }

    private synchronized void loadCountryCodes() {
        if (!countriesMap.isEmpty()) {
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(ApplicationLoader.applicationContext.getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                countriesMap.put(args[2], args[0]);
                codesMap.put(args[0], args[2]);
                if (args.length > 3) {
                    phoneFormatMap.put(args[0], args[3]);
                }
                languageMap.put(args[1], args[2]);
            }
            reader.close();
        } catch (Exception ignored) {
        }
    }
}
