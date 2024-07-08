package org.telegram.messenger.partisan.masked_passcode_screen.calculator;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.masked_passcode_screen.MaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_passcode_screen.PasscodeEnteredDelegate;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.math.BigDecimal;
import java.util.ArrayList;

public class CalculatorPasscodeScreen implements MaskedPasscodeScreen {
    private PasscodeEnteredDelegate delegate;
    private Context context;

    private final int BUTTON_X_MARGIN = 28;
    private final int BUTTON_Y_MARGIN = 16;
    private final int BUTTON_SIZE = 80;

    private FrameLayout backgroundFrameLayout;
    private String inputString = "";
    private TextView inputEditText;
    private TextView outputEditText;
    private FrameLayout buttonsContainer;
    public FrameLayout numbersFrameLayout;
    private ArrayList<Button> numberFrameLayouts;

    public CalculatorPasscodeScreen(Context context, PasscodeEnteredDelegate delegate) {
        this.context = context;
        this.delegate = delegate;
    }

    @Override
    public View createView() {

        backgroundFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.setWillNotDraw(false);

        inputEditText = new TextView(context);
        inputEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        inputEditText.setTextColor(0xFFFFFFFF);
        backgroundFrameLayout.addView(inputEditText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 70, 0, 70, 0));

        outputEditText = new TextView(context);
        outputEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 68);
        outputEditText.setTextColor(0xFFFFFFFF);
        backgroundFrameLayout.addView(outputEditText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 70, 0, 70, 0));

        buttonsContainer = new FrameLayout(context);
        backgroundFrameLayout.addView(buttonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        numbersFrameLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                if ((getParent() instanceof View)) {
                    int parentHeight = ((View) getParent()).getHeight();
                    int height = getHeight();
                    float scale = Math.min((float) parentHeight / height, 1f);

                    setPivotX(getWidth() / 2f);
                    setPivotY(((LayoutParams) getLayoutParams()).gravity == Gravity.CENTER ? getHeight() / 2f : 0);
                    setScaleX(scale);
                    setScaleY(scale);
                }
            }
        };
        buttonsContainer.addView(numbersFrameLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        numberFrameLayouts = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            Button button = new Button(context);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            ScaleStateListAnimator.apply(button, .15f, 1.5f);
            int row = i / 4;
            int col = i % 4;
            if (row >= 1 && row <= 3 && col <= 2) {
                button.setTextColor(0xffffffff);
                button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(BUTTON_SIZE / 2), 0xff323232, 0xff323232));
                int num = (row - 1) * 3 + col + 1;
                button.setTag(String.valueOf(num));
                button.setText(String.valueOf(num));
            } else if (col == 3) {
                button.setTextColor(0xffffffff);
                button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(BUTTON_SIZE / 2), 0xfffe9500, 0xfffe9500));
                String operation = null;
                if (row == 0) {
                    operation = "=";
                } else if (row == 1) {
                    operation = "+";
                } else if (row == 2) {
                    operation = "-";
                } else if (row == 3) {
                    operation = "×";
                } else if (row == 4) {
                    operation = "/";
                }
                button.setTag(operation);
                button.setText(operation);
            } else if (row == 4) {
                button.setTextColor(0xff060606);
                button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(BUTTON_SIZE / 2), 0xffa5a5a5, 0xffa5a5a5));
                String operation = null;
                if (col == 0) {
                    operation = "AC";
                } else if (col == 1) {
                    operation = "<";
                } else if (col == 2) {
                    operation = "%";
                }
                button.setTag(operation);
                button.setText(operation.equals("<") ? "\u232b" : operation);
            } else if (i == 0) {
                button.setTextColor(0xffffffff);
                button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(BUTTON_SIZE / 2), 0xff323232, 0xff323232));
                button.setTag("0");
                button.setText("0");
            } else if (i == 1) {
                button.setVisibility(View.GONE);
            } else if (i == 2) {
                button.setTextColor(0xffffffff);
                button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(BUTTON_SIZE / 2), 0xff323232, 0xff323232));
                button.setTag(".");
                button.setText(".");
            }
            button.setOnClickListener(v -> {
                String tag = (String) v.getTag();
                if (tag.equals("=")) {
                    String outputString = outputEditText.getText().toString();
                    if (outputString.isEmpty()) {
                        clearInput();
                    } else {
                        try {
                            BigDecimal outputValue = new BigDecimal(outputString);
                            setInput(removeFractionZeroesFromString(outputValue.toPlainString()));
                        } catch (Exception ignore) {
                            clearInput();
                        }
                    }
                } else if (tag.equals("<")) {
                    deleteLastInputChar();
                } else if (tag.equals("AC")) {
                    clearInput();
                } else {
                    addCharToInput(tag.charAt(0));
                }
                if (inputEditText.length() == 4) {
                    delegate.passcodeEntered(getPasswordString());
                }
            });
            numberFrameLayouts.add(button);
        }
        for (int a = 19; a >= 0; a--) {
            Button button = numberFrameLayouts.get(a);
            if (a == 0) {
                numbersFrameLayout.addView(button, LayoutHelper.createFrame(BUTTON_SIZE + BUTTON_X_MARGIN + BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.LEFT));
            } else {
                numbersFrameLayout.addView(button, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.LEFT));
            }
        }
        return backgroundFrameLayout;
    }

    private String getPasswordString() {
        return (String) inputEditText.getText();
    }

    @Override
    public void onShow(boolean fingerprint, boolean animated) {
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (parentActivity != null) {
            View currentFocus = parentActivity.getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        }
        backgroundFrameLayout.setBackgroundColor(0xff000000);

        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN) {
            numbersFrameLayout.setVisibility(View.VISIBLE);
            inputEditText.setVisibility(View.VISIBLE);
            outputEditText.setVisibility(View.VISIBLE);
        }
        inputEditText.setText("");
        outputEditText.setText("");
        inputString = "";
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.displaySize.y - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);

        int sizeBetweenButtonsX = dp(BUTTON_X_MARGIN);
        int sizeBetweenButtonsY = dp(BUTTON_Y_MARGIN);
        int buttonSize = dp(BUTTON_SIZE);

        final boolean landscape = !AndroidUtilities.isTablet() && context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        int buttonsSumHeight = 2 * sizeBetweenButtonsY + (buttonSize + sizeBetweenButtonsY) * 4 + buttonSize;
        int targetButtonsSumHeight = (int)(0.65 * height);
        if (targetButtonsSumHeight >= buttonsSumHeight) {
            int spacesHeight = 2 * sizeBetweenButtonsY + 4 * sizeBetweenButtonsY;
            int spacesTargetHeight = targetButtonsSumHeight - 5 * buttonSize;
            float scale = (float)spacesTargetHeight / spacesHeight;
            sizeBetweenButtonsY = (int)(scale * sizeBetweenButtonsY);
        }
        for (int a = 0; a < 20; a++) {
            FrameLayout.LayoutParams layoutParams;
            int row = a / 4;
            int col = a % 4;
            Button button = numberFrameLayouts.get(a);
            layoutParams = (FrameLayout.LayoutParams) button.getLayoutParams();
            layoutParams.bottomMargin = 2 * sizeBetweenButtonsY + (buttonSize + sizeBetweenButtonsY) * row;
            layoutParams.leftMargin = (buttonSize + sizeBetweenButtonsX) * col;
            button.setLayoutParams(layoutParams);
        }

        if (landscape) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) buttonsContainer.getLayoutParams();
            layoutParams.height = height;
            layoutParams.leftMargin = width / 2;
            layoutParams.topMargin = height - layoutParams.height + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            layoutParams.width = width / 2;
            buttonsContainer.setLayoutParams(layoutParams);

            int cols = 4;
            int rows = 5;
            layoutParams = (FrameLayout.LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = dp(82) + buttonSize * rows + sizeBetweenButtonsY * Math.max(0, rows - 1);
            layoutParams.width =  buttonSize * cols + sizeBetweenButtonsX * Math.max(0, cols - 1);
            layoutParams.gravity = Gravity.CENTER;
            numbersFrameLayout.setLayoutParams(layoutParams);
        } else {
            int left = 0;
            if (AndroidUtilities.isTablet()) {
                if (width > dp(498)) {
                    left = (width - dp(498)) / 2;
                    width = dp(498);
                }
            }

            int cols = 4;
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = dp(82) + targetButtonsSumHeight;
            layoutParams.width =  buttonSize * cols + sizeBetweenButtonsX * Math.max(0, cols - 1);
            if (AndroidUtilities.isTablet()) {
                layoutParams.gravity = Gravity.CENTER;
            } else {
                layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            }
            numbersFrameLayout.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) buttonsContainer.getLayoutParams();
            layoutParams.leftMargin = left;
            layoutParams.bottomMargin = 0;
            layoutParams.width = width;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            buttonsContainer.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) outputEditText.getLayoutParams();
            layoutParams.bottomMargin = buttonsSumHeight + buttonSize + 2 * sizeBetweenButtonsY;
            layoutParams.rightMargin = sizeBetweenButtonsX;
            outputEditText.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) inputEditText.getLayoutParams();
            layoutParams.bottomMargin = buttonsSumHeight + buttonSize + 2 * sizeBetweenButtonsY + AndroidUtilities.dp(68) + 2 * sizeBetweenButtonsY;
            layoutParams.rightMargin = sizeBetweenButtonsX;
            inputEditText.setLayoutParams(layoutParams);
        }
    }

    private void deleteLastInputChar() {
        if (inputString.isEmpty()) {
            return;
        }
        inputString = inputString.substring(0, inputString.length() - 1);
        inputEditText.setText(inputString);
        updateOutput();
    }

    private void clearInput() {
        inputString = "";
        inputEditText.setText(inputString);
        updateOutput();
    }

    private void setInput(String input) {
        inputString = input;
        inputEditText.setText(inputString);
        updateOutput();
    }

    private void addCharToInput(char c) {
        if (inputString.isEmpty()) {
            if ("+×/%.".contains(String.valueOf(c))) {
                return;
            }
        } else {
            char lastChar = inputString.charAt(inputString.length() - 1);
            if (c == '.') {
                if (!"0123456789".contains(String.valueOf(lastChar))) {
                    return;
                }
                for (int i = inputString.length() - 1; i >= 0; i--) {
                    char currentChar = inputString.charAt(i);
                    if (currentChar == '.') {
                        return;
                    } else if (!"0123456789".contains(String.valueOf(currentChar))) {
                        break;
                    }
                }
            } else if ("+-×/".contains(String.valueOf(c))) {
                if ("+-×/".contains(String.valueOf(lastChar))) {
                    inputString = inputString.substring(0, inputString.length() - 1);
                }
            } else if (c == '%') {
                if (!"0123456789%".contains(String.valueOf(lastChar))) {
                    return;
                }
            }
        }
        inputString += c;
        inputEditText.setText(inputString);
        updateOutput();
    }

    private void updateOutput() {
        try {
            BigDecimal result = CalculatorHelper.calculateExpression(inputString);
            outputEditText.setText(formatBigDecimal(result));
        } catch (Exception ignore) {
            outputEditText.setText("Error");
        }
    }

    private static String formatBigDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        } else if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        } else {
            String plainOutput = value.toPlainString();
            if (plainOutput.length() <= 10) {
                return removeFractionZeroesFromString(plainOutput);
            } else {
                String output = String.format("%." + Math.min(value.precision(), 7) + "g", value);
                return removeFractionZeroesFromString(output);
            }
        }
    }

    private static String removeFractionZeroesFromString(String output) {
        if (output.contains(".")) {
            while (output.endsWith("0")) {
                output = output.substring(0, output.length() - 1);
            }
        }
        if (output.endsWith(".")) {
            output = output.substring(0, output.length() - 1);
        }
        return output;
    }
}
