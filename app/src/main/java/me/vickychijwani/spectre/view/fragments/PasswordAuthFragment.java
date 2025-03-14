package me.vickychijwani.spectre.view.fragments;

import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.vickychijwani.spectre.R;
import me.vickychijwani.spectre.auth.LoginOrchestrator;
import me.vickychijwani.spectre.databinding.FragmentLoginUrlBinding;
import me.vickychijwani.spectre.databinding.FragmentPasswordAuthBinding;
import me.vickychijwani.spectre.util.AppUtils;
import me.vickychijwani.spectre.util.KeyboardUtils;
import me.vickychijwani.spectre.util.Listenable;
import me.vickychijwani.spectre.util.NetworkUtils;
import me.vickychijwani.spectre.util.log.Log;
import me.vickychijwani.spectre.view.AboutActivity;
import me.vickychijwani.spectre.view.LoginActivity;

public class PasswordAuthFragment extends BaseFragment implements
        TextView.OnEditorActionListener,
        LoginOrchestrator.Listener
{
private FragmentPasswordAuthBinding binding;
/*
    @BindView(R.id.email)                   EditText mEmailView;
    @BindView(R.id.email_error)             TextView mEmailErrorView;
    @BindView(R.id.password)                EditText mPasswordView;
    @BindView(R.id.password_error)          TextView mPasswordErrorView;
    @BindView(R.id.login_help_tip)          TextView mLoginHelpTipView;
    @BindView(R.id.sign_in_btn)             View mSignInBtn;
    @BindView(R.id.progress)                ProgressBar mProgress;
*/

    private Listenable<LoginOrchestrator.Listener> mLoginOrchestrator = null;

    public static PasswordAuthFragment newInstance() {
        PasswordAuthFragment fragment = new PasswordAuthFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = FragmentPasswordAuthBinding.inflate(inflater, container, false);
//        bindView(view);
        binding.password.setOnEditorActionListener(this);

        final String loginHelpTip = binding.loginHelpTip.getText().toString();
        AppUtils.setHtmlWithLinkClickHandler(binding.loginHelpTip, loginHelpTip, (url) -> {
            if ("login-help".equals(url)) {
                AppUtils.openUri(PasswordAuthFragment.this, AboutActivity.URL_COMMUNITY);
            } else {
                Log.wtf("Unexpected URL = %s", url);
            }
        });

        return binding.getRoot();

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        KeyboardUtils.focusAndShowKeyboard(getActivity(), binding.email);
        mLoginOrchestrator = ((LoginActivity) getActivity()).getLoginOrchestratorListenable();
    }

    @Override
    public void onStart() {
        super.onStart();
        mLoginOrchestrator.listen(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        stopWaiting();
        mLoginOrchestrator.unlisten(this);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == getResources().getInteger(R.integer.ime_action_id_signin)
                || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
            onSignInClicked();
            // don't consume the event, so the keyboard can also be hidden
            // http://stackoverflow.com/questions/2342620/how-to-hide-keyboard-after-typing-in-edittext-in-android#comment20849208_10184099
            return false;
        }
        return false;
    }

   /* @OnClick(R.id.email_layout)*/
    public void onEmailLayoutClicked() {
        KeyboardUtils.focusAndShowKeyboard(getActivity(), binding.email);
    }

//    @OnClick(R.id.password_layout)
    public void onPasswordLayoutClicked() {
        KeyboardUtils.focusAndShowKeyboard(getActivity(), binding.password);
    }

//    @OnClick(R.id.sign_in_btn)
    public void onSignInClicked() {
        if (! NetworkUtils.isConnected(getActivity())) {
            Toast.makeText(getActivity(), R.string.no_internet_connection, Toast.LENGTH_SHORT).show();
            return;
        }

        String email = binding.email.getText().toString().trim();
        String password = binding.password.getText().toString().trim();

        boolean hasError = false;
        View focusView = null;

        // check for a valid email address
        if (TextUtils.isEmpty(email)) {
            showEmailError(getString(R.string.error_field_required));
            focusView = binding.email;
            hasError = true;
        } else if (! isEmailValid(email)) {
            showEmailError(getString(R.string.error_invalid_email));
            focusView = binding.email;
            hasError = true;
        } else {
            showEmailError(null);
        }

        // check for a non-empty password
        if (TextUtils.isEmpty(password)) {
            showPasswordError(getString(R.string.error_field_required));
            focusView = binding.password;
            hasError = true;
        } else {
            showPasswordError(null);
        }

        if (hasError) {
            // there was an error; focus the first form field with an error
            focusView.requestFocus();
        } else {
            // actual login attempt
            startWaiting();
            ((LoginActivity) getActivity()).onEmailAndPassword(email, password);
        }
    }

    @Override
    public void onStartWaiting() {
        startWaiting();
    }

    @Override
    public void onBlogUrlError(LoginOrchestrator.ErrorType errorType, @NonNull Throwable error,
                               @NonNull String blogUrl) {
        // no-op
    }

    @Override
    public void onApiError(String error, boolean isEmailError) {
        stopWaiting();
        EditText errorView = binding.password;
        TextView errorMsgView = binding.passwordError;
        if (isEmailError) {
            errorView = binding.email;
            errorMsgView = binding.emailError;
        }
        errorView.setSelection(errorView.getText().length());
        KeyboardUtils.focusAndShowKeyboard(getActivity(), errorView);
        errorMsgView.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            errorMsgView.setText(Html.fromHtml(error, Html.FROM_HTML_MODE_LEGACY));
        } else {
            errorMsgView.setText(Html.fromHtml(error));
        }
        // show the help tip, and let it stay there; no need to hide it again
        binding.loginHelpTip.setVisibility(View.VISIBLE);
    }

    @Override
    public void onGhostV0Error() {
        // no-op
    }

    @Override
    public void onNetworkError(LoginOrchestrator.ErrorType errorType, @NonNull Throwable error) {
        switch (errorType) {
            case ERR_CONNECTION:
                showEmailError(getString(R.string.login_connection_error));
                break;
            case ERR_USER_NETWORK:
                showEmailError(getString(R.string.no_such_blog));
                break;
            case ERR_SSL:
                showEmailError(getString(R.string.login_ssl_unsupported));
                break;
            case ERR_UNKNOWN:
                showEmailError(getString(R.string.login_unexpected_error));
                break;
        }
    }

    @Override
    public void onLoginDone() {
        // no-op
    }

    private void startWaiting() {
        allowInput(false);
        showEmailError(null);
        showPasswordError(null);
        binding.progress.setVisibility(View.VISIBLE);
        binding.signInBtn.setVisibility(View.INVISIBLE);
    }

    private void stopWaiting() {
        allowInput(true);
        binding.progress.setVisibility(View.INVISIBLE);
        binding.signInBtn.setVisibility(View.VISIBLE);
    }

    private void allowInput(boolean allow) {
        binding.email.setEnabled(allow);
        binding.password.setEnabled(allow);
        binding.signInBtn.setEnabled(allow);
        if (!allow) {
            // hide the help tip since it contains a clickable link
            binding.loginHelpTip.setVisibility(View.INVISIBLE);
        }
    }

    private void showEmailError(@Nullable String error) {
        if (error == null || error.isEmpty()) {
            binding.emailError.setText("");
            binding.emailError.setVisibility(View.GONE);
        } else {
            binding.emailError.setText(error);
            binding.emailError.setVisibility(View.VISIBLE);
        }
    }

    private void showPasswordError(@Nullable String error) {
        if (error == null || error.isEmpty()) {
            binding.passwordError.setText("");
            binding.passwordError.setVisibility(View.INVISIBLE);
        } else {
            binding.passwordError.setText(error);
            binding.passwordError.setVisibility(View.VISIBLE);
        }
    }

    private static boolean isEmailValid(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

}
