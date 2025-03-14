package me.vickychijwani.spectre.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;


//import me.vickychijwani.spectre.BuildConfig;
import me.vickychijwani.spectre.BuildConfig;
import me.vickychijwani.spectre.R;
import me.vickychijwani.spectre.SpectreApplication;
import me.vickychijwani.spectre.account.AccountManager;
import me.vickychijwani.spectre.databinding.ActivityPostListBinding;
import me.vickychijwani.spectre.error.SyncException;
import me.vickychijwani.spectre.event.BlogSettingsLoadedEvent;
import me.vickychijwani.spectre.event.CreatePostEvent;
import me.vickychijwani.spectre.event.DataRefreshedEvent;
import me.vickychijwani.spectre.event.ForceCancelRefreshEvent;
import me.vickychijwani.spectre.event.LogoutEvent;
import me.vickychijwani.spectre.event.LogoutStatusEvent;
import me.vickychijwani.spectre.event.PostConflictFoundEvent;
import me.vickychijwani.spectre.event.PostCreatedEvent;
import me.vickychijwani.spectre.event.PostsLoadedEvent;
import me.vickychijwani.spectre.event.RefreshDataEvent;
import me.vickychijwani.spectre.event.UserLoadedEvent;
import me.vickychijwani.spectre.model.entity.Post;
import me.vickychijwani.spectre.model.entity.Setting;
import me.vickychijwani.spectre.network.GhostApiUtils;
import me.vickychijwani.spectre.util.DeviceUtils;
import me.vickychijwani.spectre.util.log.Log;
import me.vickychijwani.spectre.view.image.BorderedCircleTransformation;
import me.vickychijwani.spectre.view.widget.SpaceItemDecoration;
import retrofit2.Response;

import static me.vickychijwani.spectre.util.NetworkUtils.isConnectionError;
import static me.vickychijwani.spectre.util.NetworkUtils.makePicassoUrl;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class PostListActivity extends BaseActivity {

    private static final String TAG = "PostListActivity";
    private static final int REQUEST_CODE_VIEW_POST = 1;

    private final List<Post> mPosts = new ArrayList<>();
    private PostAdapter mPostAdapter;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mRefreshDataRunnable;
    private Runnable mRefreshTimeoutRunnable;

    private static final int REFRESH_FREQUENCY = 10 * 60 * 1000;    // in milliseconds

    // NOTE: very large timeout is needed for cases like initial sync on a blog with 100s of posts
    private static final int REFRESH_TIMEOUT = 5 * 60 * 1000;       // in milliseconds
    private ActivityPostListBinding binding;
 /*   @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.app_bar_bg)                  View mAppBarBg;
    @BindView(R.id.user_image)                  ImageView mUserImageView;
    @BindView(R.id.user_blog_title)             TextView mBlogTitleView;
    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.post_list_container)         FrameLayout mPostListContainer;
    @BindView(R.id.post_list)
    RecyclerView mPostList;

    @BindView(R.id.new_post_reveal)             View mNewPostRevealView;
    @BindView(R.id.new_post_reveal_shrink)      View mNewPostRevealShrinkView;*/
//    @BindDimen(R.dimen.toolbar_height)      int mToolbarHeight;
//    @BindDimen(R.dimen.tabbar_height)       int mTabbarHeight;
    @ColorInt
    private                       int mColorAccent;
    @ColorInt private                       int mColorPrimary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (! AccountManager.hasActiveBlog()) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        if (! AccountManager.getActiveBlog().isLoggedIn()) {
            // is it safe to infer that an active blog which is not logged in must mean the
            // password has changed or Ghost Auth code is expired?
            credentialsExpired();
        }

        binding = ActivityPostListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        // TODO: 27/02/2025
        if (BuildConfig.DEBUG) {
            SpectreApplication.getInstance().addDebugDrawer(this);
        }

        // get rid of the default action bar confetti
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayOptions(0);

        // constants for animation
        TypedValue typedColorValue = new TypedValue();
        getTheme().resolveAttribute(R.color.status_deleted, typedColorValue, true);
//        getTheme().resolveAttribute(R.attr.colorAccent, typedColorValue, true);
        mColorAccent = typedColorValue.data;
        getTheme().resolveAttribute(R.color.status_deleted, typedColorValue, true);
//        getTheme().resolveAttribute(R.attr.colorPrimary, typedColorValue, true);
        mColorPrimary = typedColorValue.data;

        // initialize post list UI
        final String activeBlogUrl = AccountManager.getActiveBlogUrl();
        mPostAdapter = new PostAdapter(this, mPosts, activeBlogUrl, getPicasso(), v -> {
            int pos = binding.postList.getChildLayoutPosition(v);
            if (pos == RecyclerView.NO_POSITION) return;
            Post post = (Post) mPostAdapter.getItem(pos);
            if (! GhostApiUtils.INSTANCE.hasOnlyMarkdownCard(post.getMobiledoc())) {
                Snackbar.make(binding.postList, R.string.koenig_post_error,
                        Snackbar.LENGTH_SHORT).show();
                return;
            }
            if (post.isMarkedForDeletion()) {
                Snackbar.make(binding.postList, R.string.status_marked_for_deletion_open_error,
                        Snackbar.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(PostListActivity.this, PostViewActivity.class);
            intent.putExtra(BundleKeys.POST, post);
            intent.putExtra(BundleKeys.START_EDITING, false);
            Bundle activityOptions = ActivityOptions.makeScaleUpAnimation(v, 0, 0,
                    v.getWidth(), v.getHeight()).toBundle();
            startActivityForResult(intent, REQUEST_CODE_VIEW_POST, activityOptions);
        });
        binding.postList.setAdapter(mPostAdapter);
        binding.postList.setLayoutManager(new StaggeredGridLayoutManager(
                getResources().getInteger(R.integer.post_grid_num_columns),
                StaggeredGridLayoutManager.VERTICAL));
        binding.postList.setItemAnimator(new DefaultItemAnimator());
        int hSpace = getResources().getDimensionPixelOffset(R.dimen.card_grid_hspace);
        int vSpace = getResources().getDimensionPixelOffset(R.dimen.card_grid_vspace);
        binding.postList.addItemDecoration(new SpaceItemDecoration(hSpace, vSpace));

        // use a fixed-width grid on large screens
        int screenWidth = DeviceUtils.getScreenWidth(this);
        int maxContainerWidth = getResources().getDimensionPixelSize(R.dimen.post_grid_max_width);
        if (screenWidth > maxContainerWidth) {
            int containerPadding = (screenWidth - maxContainerWidth) / 2;
            ViewCompat.setPaddingRelative(binding.toolbar,
                    ViewCompat.getPaddingStart(binding.toolbar) + containerPadding,
                    binding.toolbar.getPaddingTop(),
                    ViewCompat.getPaddingEnd(binding.toolbar) + containerPadding,
                    binding.toolbar.getPaddingBottom());
            ViewCompat.setPaddingRelative(binding.postList,
                    ViewCompat.getPaddingStart(binding.postList) + containerPadding,
                    binding.postList.getPaddingTop(),
                    ViewCompat.getPaddingEnd(binding.postList) + containerPadding,
                    binding.postList.getPaddingBottom());
        }

        final Drawable appbarShadowDrawable;
        appbarShadowDrawable = ContextCompat.getDrawable(this, R.drawable.appbar_shadow);
        binding.postListContainer.setForeground(null);     // hide the shadow initially
        binding.postList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int scrollY = binding.postList.computeVerticalScrollOffset();
                binding.appBarBg.setTranslationY(-scrollY);
                binding.postListContainer.setForeground(scrollY <= 0 ? null : appbarShadowDrawable);
            }
        });

        mRefreshDataRunnable = () -> refreshData(false);
        mRefreshTimeoutRunnable = this::refreshTimedOut;
        binding.swipeRefreshLayout.setColorSchemeColors(mColorAccent, mColorPrimary);
        binding.swipeRefreshLayout.setOnRefreshListener(() -> refreshData(false));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // load cached data immediately
        refreshData(true);
        // reset views involved in new post animation
        binding.newPostReveal.setVisibility(View.INVISIBLE);
        binding.newPostRevealShrink.setScaleY(1f);
        binding.newPostRevealShrink.setBackgroundColor(mColorAccent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelDataRefresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (binding.postList != null) {
            // cancel any ongoing image requests, courtesy http://stackoverflow.com/a/33961706/504611
            // not doing this in onPause or onStop because there we wouldn't want to clear the list itself
            binding.postList.setAdapter(null);
        }
        mRefreshDataRunnable = null;    // the runnable holds an implicit reference to the activity!
                                        // allow it to get GC'ed to avoid a memory leak
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.post_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_view_homepage) {
            startBrowserActivity(AccountManager.getActiveBlogUrl());
            return true;
        } else if (itemId == R.id.action_refresh) {
            refreshData(false);
            return true;
        } else if (itemId == R.id.action_about) {
            Intent aboutIntent = new Intent(this, AboutActivity.class);
            startActivity(aboutIntent);
            return true;
        } else if (itemId == R.id.action_logout) {
            getBus().post(new LogoutEvent(AccountManager.getActiveBlogUrl(), false));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_VIEW_POST && resultCode == PostViewActivity.RESULT_CODE_DELETED) {
            Snackbar.make(binding.postList, R.string.post_deleted, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Subscribe
    public void onDataRefreshedEvent(DataRefreshedEvent event) {
        binding.swipeRefreshLayout.setRefreshing(false);
        cancelRefreshTimeout();
        scheduleDataRefresh();

        if (event.apiFailure == null) {
            return;
        }

        Throwable error = event.apiFailure.error;
        Response response = event.apiFailure.response;
        if (error != null && isConnectionError(error)) {
            Toast.makeText(this, R.string.network_timeout, Toast.LENGTH_LONG).show();
        } else {
            Log.e(TAG, "Generic error message triggered during refresh");
            if (error != null) {
                Log.exception(new SyncException("sync failed", error));
            } else if (response != null) {
                try {
                    Log.exception(new SyncException("Response: " + response.errorBody().string()));
                } catch (Exception exception) {
                    Log.exception(new SyncException("Sync failed, but threw this when trying to " +
                            "log response", exception));
                }
            }
        }
        Toast.makeText(this, R.string.refresh_failed, Toast.LENGTH_LONG).show();
    }

    @Subscribe
    public void onUserLoadedEvent(UserLoadedEvent event) {
        if (event.user.getProfileImage() != null) {
            if (event.user.getProfileImage().isEmpty()) {
                return;
            }
            String blogUrl = AccountManager.getActiveBlogUrl();
            String imageUrl = makePicassoUrl(blogUrl, event.user.getProfileImage());
            getPicasso()
                    .load(imageUrl)
                    .transform(new BorderedCircleTransformation())
                    .fit()
                    .into(binding.userImage);
        } else {
            // As of Ghost v2.13.1 (possibly earlier), profile image is null if not set
            Log.w(TAG, "user image is null!");
        }
    }

    @Subscribe
    public void onBlogSettingsLoadedEvent(BlogSettingsLoadedEvent event) {
        String blogTitle = getString(R.string.app_name);
        for (Setting setting : event.settings) {
            if (setting.getKey().equals("title")) {
                blogTitle = setting.getValue();
            }
        }
        binding.userBlogTitle.setText(blogTitle);
    }

    @Subscribe
    public void onPostsLoadedEvent(PostsLoadedEvent event) {
        // this exists to let animation run to completion because posts are loaded
        // twice on launch: once cached data, and once from the network
        if (mPosts.equals(event.posts)) {
            return;
        }
        mPosts.clear();
        mPosts.addAll(event.posts);
        if (mPosts.size() >= event.postsFetchLimit) {
            CharSequence message = Html.fromHtml(getString(R.string.post_limit_exceeded,
                    getString(R.string.app_name), event.postsFetchLimit,
                    "https://github.com/TryGhost/Ghost-Android/issues/15"));
            mPostAdapter.showFooter(message);
        } else {
            mPostAdapter.hideFooter();
        }
        mPostAdapter.notifyDataSetChanged();
    }

//    @OnClick(R.id.new_post_btn)
    public void onNewPostBtnClicked(View btn) {
        Runnable createPost = () -> getBus().post(new CreatePostEvent());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // circular reveal animation
            int[] revealViewLocation = new int[2], btnLocation = new int[2];
            binding.newPostReveal.getLocationOnScreen(revealViewLocation);
            btn.getLocationOnScreen(btnLocation);
            int centerX = btnLocation[0] - revealViewLocation[0] + btn.getWidth()/2;
            int centerY = btnLocation[1] - revealViewLocation[1] + btn.getHeight()/2;
            float endRadius = (float) Math.hypot(centerX, centerY);
            Animator revealAnimator = ViewAnimationUtils.createCircularReveal(
                    binding.newPostReveal, centerX, centerY, 0, endRadius);
            revealAnimator.setDuration(500);
            revealAnimator.setInterpolator(new AccelerateInterpolator());
            binding.newPostReveal.setVisibility(View.VISIBLE);

            // background color animation
            ValueAnimator colorAnimator = ValueAnimator
                    .ofObject(new ArgbEvaluator(), mColorAccent, mColorPrimary);
            colorAnimator.addUpdateListener(animator ->
                    binding.newPostRevealShrink.setBackgroundColor((int) animator.getAnimatedValue()));
            colorAnimator.setDuration(500);
            colorAnimator.setInterpolator(new AccelerateInterpolator());

            // shrink animation
            float startHeight = binding.newPostRevealShrink.getHeight();
          /*  float mToolbarHeight =  mToolbarHeight.getContext().getResources().getDimension(R.dimen.toolbar_height);
            float mTabbarHeight =  mTabbarHeight.getContext().getResources().getDimension(R.dimen.toolbar_height);
*/
//            float targetScaleY = (mToolbarHeight + mTabbarHeight) / startHeight;
            /*ObjectAnimator shrinkAnimator = ObjectAnimator.ofFloat(binding.newPostRevealShrink,
                    "scaleY", targetScaleY);
            shrinkAnimator.setStartDelay(150);
            shrinkAnimator.setDuration(300);
            shrinkAnimator.setInterpolator(new DecelerateInterpolator());*/

            // play reveal + color change together, followed by shrink
           /* AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.play(revealAnimator).with(colorAnimator);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    shrinkAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            createPost.run();
                        }
                    });
                    shrinkAnimator.start();
                }
            });*/
//            animatorSet.start();
        } else {
            createPost.run();
        }
    }

    @Subscribe
    public void onPostCreatedEvent(PostCreatedEvent event) {
        Intent intent = new Intent(PostListActivity.this, PostViewActivity.class);
        intent.putExtra(BundleKeys.POST, event.newPost);
        intent.putExtra(BundleKeys.START_EDITING, true);
        startActivityForResult(intent, REQUEST_CODE_VIEW_POST);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // setup the next Activity to fade-in, since we just finished the circular reveal
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    @Subscribe
    public void onLogoutStatusEvent(LogoutStatusEvent event) {
        if (!event.succeeded && event.hasPendingActions) {
            final AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.unsynced_changes_msg))
                    .setPositiveButton(R.string.dont_logout, (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.logout, (dialog, which) -> {
                        dialog.dismiss();
                        getBus().post(new LogoutEvent(AccountManager.getActiveBlogUrl(), true));
                    })
                    .create();
            alertDialog.show();
        } else {
            finish();
            Intent logoutIntent = new Intent(this, LoginActivity.class);
            startActivity(logoutIntent);
        }
    }

    @Subscribe
    public void onPostConflictFoundEvent(PostConflictFoundEvent event) {
        Intent intent = new Intent(this, PostConflictResolutionActivity.class);
        intent.putExtra(BundleKeys.LOCAL_POST, event.localPost);
        intent.putExtra(BundleKeys.SERVER_POST, event.serverPost);
        startActivity(intent);
    }

    // private methods
    private void scheduleDataRefresh() {
        // cancel already-scheduled refresh event
        cancelDataRefresh();
        // NOTE do not pass this::refreshData directly, because that creates a new Runnable and
        // hence cannot be removed using Handler.removeCallbacks later, indirectly causing the
        // entire Activity to leak!
        mHandler.postDelayed(mRefreshDataRunnable, REFRESH_FREQUENCY);
    }

    private void cancelDataRefresh() {
        mHandler.removeCallbacks(mRefreshDataRunnable);
        cancelRefreshTimeout();
    }

    private void refreshData(boolean loadCachedData) {
        getBus().post(new RefreshDataEvent(loadCachedData));
        mHandler.postDelayed(mRefreshTimeoutRunnable, REFRESH_TIMEOUT);
    }

    private void cancelRefreshTimeout() {
        mHandler.removeCallbacks(mRefreshTimeoutRunnable);
    }

    private void refreshTimedOut() {
        getBus().post(new ForceCancelRefreshEvent());
        binding.swipeRefreshLayout.setRefreshing(false);
        Toast.makeText(this, R.string.refresh_failed, Toast.LENGTH_LONG).show();
        scheduleDataRefresh();
    }

}
