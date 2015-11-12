package vietnamworks.com.pal.fragments;

import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.firebase.client.DataSnapshot;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.firebase.client.ValueEventListener;
import com.getbase.floatingactionbutton.FloatingActionsMenu;

import java.util.HashMap;

import vietnamworks.com.pal.R;
import vietnamworks.com.pal.activities.BaseActivity;
import vietnamworks.com.pal.activities.TimelineActivity;
import vietnamworks.com.pal.common.Utils;
import vietnamworks.com.pal.configurations.AppUiConfig;
import vietnamworks.com.pal.custom_views.TimelineItemBaseView;
import vietnamworks.com.pal.custom_views.TimelineItemNullView;
import vietnamworks.com.pal.custom_views.TimelineItemQuest;
import vietnamworks.com.pal.custom_views.TimelineItemView;
import vietnamworks.com.pal.entities.Post;
import vietnamworks.com.pal.models.AppModel;
import vietnamworks.com.pal.models.Posts;
import vietnamworks.com.pal.services.FirebaseService;
import vietnamworks.com.pal.services.GaService;

/**
 * Created by duynk on 11/2/15.
 */
public class PostListFragment extends BaseFragment {
    public final static int FILTER_ALL = 0;
    public final static int FILTER_EVALUATED = 1;
    int filterType = FILTER_ALL;


    private PostItemAdapter mAdapter;
    Query dataRef;
    RecyclerView recyclerView;
    View overlay, challenge_view;
    FloatingActionsMenu fab;

    int pageSize = 100;
    int dataSize = pageSize;

    private SwipeRefreshLayout swipeContainer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filterType = getArguments().getInt("mode");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout containing a title and body text.
        ViewGroup rootView = (ViewGroup) inflater
                .inflate(R.layout.fragment_posts, container, false);

        BaseActivity.applyFont(rootView);

        mAdapter = new PostItemAdapter(getContext());

        recyclerView = (RecyclerView) rootView.findViewById(R.id.post_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this.getActivity()));
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                mAdapter.setFirstVisibleItem(((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition());
            }
        });

        swipeContainer = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeContainer);
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                fab.setVisibility(View.INVISIBLE);
                openOverlay();
                BaseActivity.timeout(new Runnable() {
                    @Override
                    public void run() {
                        fab.setVisibility(View.VISIBLE);
                        swipeContainer.setRefreshing(false);
                        closeOverlay();
                    }
                }, 1000);
            }
        });

        challenge_view = ((TimelineActivity)BaseActivity.sInstance).challenge_view;
        challenge_view.setVisibility(View.GONE);

        overlay = rootView.findViewById(R.id.overlay);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.collapse();
                return;
            }
        });
        fab = (FloatingActionsMenu) rootView.findViewById(R.id.fab);

        fab.setOnFloatingActionsMenuUpdateListener(new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener() {
            @Override
            public void onMenuExpanded() {
                openOverlay();
            }

            @Override
            public void onMenuCollapsed() {
                closeOverlay();
            }
        });

        return rootView;
    }

    private void openOverlay() {
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0);
        overlay.animate().alpha(AppUiConfig.BASE_OVERLAY_ALPHA).setDuration(100).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                overlay.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {

            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        }).start();
    }

    private void closeOverlay() {
        overlay.animate().alpha(0f).setDuration(100).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                overlay.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        recyclerView.setAdapter(null);
        Activity _act = this.getActivity();
        if (_act != null) {
            TimelineActivity act = (TimelineActivity) _act;
            int mode = filterType;
            if (mode == FILTER_ALL) {
                dataRef = Posts.getAllPostsQuery().limitToFirst(dataSize);
            } else if (mode == FILTER_EVALUATED) {
                dataRef = Posts.getEvaluatedPostsQuery().limitToFirst(dataSize);
            }
            dataRef.addValueEventListener(dataValueEventListener);
            dataRef.keepSynced(true);
        }
        recyclerView.setAdapter(mAdapter);
        fab.collapseImmediately();
        overlay.setVisibility(View.GONE);
        GaService.trackScreen(R.string.ga_screen_post_list);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (dataRef != null) {
            dataRef.removeEventListener(dataValueEventListener);
        }
    }

    private ValueEventListener dataValueEventListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            AppModel.posts.getData().clear();
            for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                String key = postSnapshot.getKey();
                HashMap<String, Object> obj = postSnapshot.getValue(HashMap.class);
                Post p = new Post(obj);
                p.setId(key);
                AppModel.posts.getData().add(0, p);
                //TODO: no need to reload all list like this. Just reload changed item only
            }
            BaseActivity.timeout(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            }, 1000);
        }

        @Override
        public void onCancelled(FirebaseError firebaseError) {
        }
    };

    public static PostListFragment createAllPosts() {
        PostListFragment fragment = new PostListFragment();
        Bundle args = new Bundle();
        args.putInt("mode", FILTER_ALL);
        fragment.setArguments(args);
        return fragment;
    }

    public static PostListFragment createEvaluatedList() {
        PostListFragment fragment = new PostListFragment();
        Bundle args = new Bundle();
        args.putInt("mode", FILTER_EVALUATED);
        fragment.setArguments(args);
        return fragment;
    }

    public int getFilterType() {
        return filterType;
    }

    public void refresh() {
        mAdapter.notifyDataSetChanged();
    }

    class PostItemAdapter extends RecyclerView.Adapter<TimelineItemBaseView> {
        int lastPosition = -1;
        final int TYPE_CHALLENGE = 0;
        final int TYPE_FEED = 1;
        final int TYPE_FOOTER = 2;

        boolean hasChallenge = true;
        int count = 0;
        Context context;

        int lastVisibleItem = -1;

        public PostItemAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            hasChallenge = true;
            for (int i = 0; i < 10 && i < AppModel.posts.getData().size(); i ++) {
                if (AppModel.posts.getData().get(i).getStatus() == Post.STATUS_READY) {
                    //hasChallenge = false;
                    break;
                }
            }
            if (hasChallenge) {
                count = AppModel.posts.getData().size() + 2;
            } else {
                count = AppModel.posts.getData().size() + 1;
            }
            return count;
        }

        @Override
        public TimelineItemBaseView onCreateViewHolder(ViewGroup viewGroup, int type) {
            if (type == TYPE_FEED) {
                View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.cv_timeline_item, viewGroup, false);
                return new TimelineItemView(v, viewGroup.getContext());
            } else if (type == TYPE_FOOTER) {
                View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.cv_timeline_end, viewGroup, false);
                return new TimelineItemNullView(v);
            } else {
                View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.cv_random_quest, viewGroup, false);
                return new TimelineItemQuest(v, viewGroup.getContext());
            }
        }

        private boolean isFirstFeedRecord(int position) {
            return hasChallenge?(position == 1):(position == 0);
        }

        private int feedIndex(int pos) {
            return hasChallenge?(pos - 1):(pos);
        }

        @Override
        public int getItemViewType(int position) {
            if (hasChallenge) {
                return position == 0 ? TYPE_CHALLENGE : ((count > 0 && position < count - 1) ? TYPE_FEED : TYPE_FOOTER);
            } else {
                return (count > 0 && position < count - 1) ? TYPE_FEED : TYPE_FOOTER;
            }
        }

        @Override
        public void onBindViewHolder(final TimelineItemBaseView v, final int i) {
            int type = getItemViewType(i);
            if (type == TYPE_FEED) {
                final TimelineItemView view = (TimelineItemView)v;
                Post p = AppModel.posts.getData().get(feedIndex(i));
                if (p != null) {
                    view.setItemId(p.getId());
                    int icon = R.drawable.ic_queueing;
                    if (p.getStatus() == Post.STATUS_ADVISOR_PROCESSING) {
                        icon = R.drawable.ic_evaluating;
                    } else if (p.getStatus() == Post.STATUS_ADVISOR_EVALUATED) {
                        icon = R.drawable.ic_evaluated;
                    } else if (p.getStatus() < Post.STATUS_READY) {
                        icon = R.drawable.timeline_upload_anim_01;
                    }

                    view.highlight(!p.isHas_read());
                    view.setClickEventListener(new TimelineItemView.OnClickEventListener() {
                        @Override
                        public void onClicked(final String itemId) {
                            Posts.markAsRead(itemId);
                            BaseActivity.sInstance.setTimeout(new Runnable() {
                                @Override
                                public void run() {
                                    Bundle b = new Bundle();
                                    b.putString("id", itemId);
                                    BaseActivity.sInstance.openFragment(PostDetailFragment.create(b), R.id.fragment_holder, true);
                                }
                            }, 200);
                        }
                    });

                    if (isFirstFeedRecord(i) && p.getStatus() == Post.STATUS_READY && Math.abs(Utils.getMillis() - p.getLast_modified_date()) < 3000) {
                        Animation animation = AnimationUtils.loadAnimation(context, android.R.anim.slide_in_left);
                        animation.setDuration(500);
                        v.container.startAnimation(animation);
                        animation.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {

                            }

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                view.startIconAnim();
                            }

                            @Override
                            public void onAnimationRepeat(Animation animation) {

                            }
                        });

                        icon = R.drawable.animation_timeline_uploading;

                    } else {
                        setAnimation(v.container, i);
                    }
                    view.setValue(icon, p, true);
                }
            } else if (type == TYPE_FOOTER) {
                TimelineItemNullView view = (TimelineItemNullView)v;
                long created_date = FirebaseService.getUserProfileLongValue("created_date", 0);
                if (created_date > 0) {
                    String time = Utils.getDuration(created_date);
                    view.setText(String.format(BaseActivity.sInstance.getString(R.string.joined_at), time));
                }

                if (dataSize == getItemCount() - 1) {
                    dataSize += pageSize;
                    int mode = filterType;
                    dataRef.removeEventListener(dataValueEventListener);
                    if (mode == FILTER_ALL) {
                        dataRef = Posts.getAllPostsQuery().limitToFirst(dataSize);
                    } else if (mode == FILTER_EVALUATED) {
                        dataRef = Posts.getEvaluatedPostsQuery().limitToFirst(dataSize);
                    }
                    dataRef.addValueEventListener(dataValueEventListener);
                    dataRef.keepSynced(true);
                }
                setAnimation(v.container, i);
            } else if (type == TYPE_CHALLENGE) {
                //todo load topic
            }

            /*
            int firstVisibleItem = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            System.out.println("-----------" + firstVisibleItem);
            boolean _showChallengeShortcut = hasChallenge && (firstVisibleItem > 1);
            challenge_view.setVisibility(_showChallengeShortcut ? View.VISIBLE : View.GONE);
            if (showChallengeShortcut != _showChallengeShortcut){
                showChallengeShortcut = _showChallengeShortcut;
                if (showChallengeShortcut) {
                    BaseActivity.sInstance.hideActionBar();
                } else {
                    BaseActivity.sInstance.showActionBar();
                }
            }
            */
        }

        private void setFirstVisibleItem(int firstVisibleItem) {
            if (lastVisibleItem != firstVisibleItem) {
                boolean _showChallengeShortcut = hasChallenge && (firstVisibleItem > 0);
                challenge_view.setVisibility(_showChallengeShortcut ? View.VISIBLE : View.GONE);
                lastVisibleItem = firstVisibleItem;
            }
        }

        private void setAnimation(View viewToAnimate, int position)
        {
            /*
            // If the bound view wasn't previously displayed on screen, it's animated
            if (position > lastPosition)
            {
                Animation animation = AnimationUtils.loadAnimation(context, R.anim.list_item_appear_anim);
                animation.setDuration(200);
                viewToAnimate.startAnimation(animation);
                lastPosition = position;
            }
            */
        }
    }

}
