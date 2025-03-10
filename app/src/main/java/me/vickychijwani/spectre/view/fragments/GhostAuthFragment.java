package me.vickychijwani.spectre.view.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.vickychijwani.spectre.R;
import me.vickychijwani.spectre.auth.GhostAuth;
import me.vickychijwani.spectre.databinding.FragmentGhostAuthBinding;
import me.vickychijwani.spectre.view.BundleKeys;
import me.vickychijwani.spectre.view.LoginActivity;
import me.vickychijwani.spectre.util.log.Log;

public class GhostAuthFragment extends WebViewFragment {

    private static final String TAG = GhostAuthFragment.class.getSimpleName();
    private static final String KEY_REDIRECT_URI = "key:redirect_uri";
    private FragmentGhostAuthBinding binding;
//    @BindView(R.id.progress) ProgressBar mLoadingProgress;

    public static GhostAuthFragment newInstance(@NonNull GhostAuth.Params params) {
        GhostAuthFragment fragment = new GhostAuthFragment();
        Bundle args = new Bundle();
        args.putString(BundleKeys.URL, GhostAuth.buildAuthRequestUrl(params));
        args.putInt(KEY_LAYOUT_ID, R.layout.fragment_ghost_auth);
        args.putString(KEY_REDIRECT_URI, params.redirectUri);
        fragment.setArguments(args);
        return fragment;
    }

    public GhostAuthFragment() {}

    @NonNull @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGhostAuthBinding.inflate(inflater, container, false);
        setWebChromeClient(new ProgressWebChromeClient());
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final LoginActivity authCodeListener = (LoginActivity) getActivity();
        final String redirectUri = getArguments().getString(KEY_REDIRECT_URI);

        if (redirectUri == null) {
            throw new IllegalStateException("Redirect URI bundle argument is null!");
        }

        interceptAuthCode(authCodeListener, redirectUri);
    }

    private void interceptAuthCode(LoginActivity activity, String redirectUri) {
        setWebViewClient(new DefaultWebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!url.startsWith(redirectUri)) {
                    view.loadUrl(url);
                    return false;
                }
                String authCode = GhostAuth.extractAuthCodeFromUrl(url);
                if (authCode != null) {
                    Log.d(TAG, "AUTH CODE = %s", authCode);
                    activity.onGhostAuthCode(authCode);
                    return true;
                } else {
                    throw new IllegalStateException("URL matches redirect URI but no auth code " +
                            "was found! URL = " + url);
                }
            }
        });
    }


    private class ProgressWebChromeClient extends DefaultWebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress < 100) {
                binding.progress.setVisibility(View.VISIBLE);
            } else {
                // reset the progress bar on completion, effectively hiding it from view
                binding.progress.setVisibility(View.INVISIBLE);
            }
        }
    }

}
