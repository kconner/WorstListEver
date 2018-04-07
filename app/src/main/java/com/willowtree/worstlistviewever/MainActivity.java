package com.willowtree.worstlistviewever;

import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Choreographer;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.willowtree.worstlistviewever.api.SubredditLoader;
import com.willowtree.worstlistviewever.api.model.RedditData;


public class MainActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<RedditData>, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, Choreographer.FrameCallback {
    private ListView mListView;
    private ProgressBar mProgress;
    private int loaderId = 0;

    private SoundPool soundPool;
    private int tickSoundID;
    private double hardwareFrameIntervalSeconds;
    private long lastTimestampNanoseconds = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.getDefaultSharedPreferences(this).edit().putLong("currentTime", System.currentTimeMillis()).apply();
        setContentView(R.layout.activity_main);
        mListView = (ListView) findViewById(R.id.list);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);
        mProgress = (ProgressBar) findViewById(R.id.progress);
        Bundle args = getLoaderArguments(SubredditLoader.DEFAULT_SUBREDDIT);
        getSupportLoaderManager().initLoader(loaderId, args, this);

        soundPool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
        try {
            AssetFileDescriptor afd = getAssets().openFd("sounds/GeigerCounterTick.wav");
            tickSoundID = soundPool.load(afd, 1);
        } catch (Exception exception) {
            Log.e("Geiger Counter", exception.toString());
        }
        hardwareFrameIntervalSeconds = 1.0 / getWindowManager().getDefaultDisplay().getRefreshRate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Choreographer.getInstance().removeFrameCallback(this);
    }

    private Bundle getLoaderArguments(String subreddit){
        Bundle args = new Bundle();
        args.putString(SubredditLoader.SUBREDDIT, subreddit);
        return args;
    }

    @Override
    public Loader<RedditData> onCreateLoader(int id, Bundle args) {
        showProgress(true);
        String subreddit = args.getString(SubredditLoader.SUBREDDIT);
        getSupportActionBar().setTitle(subreddit);
        return new SubredditLoader(this, subreddit);
    }

    @Override
    public void onLoadFinished(Loader<RedditData> loader, RedditData data) {
        mListView.setAdapter(new WorstAdapter(this, data.data.children));
        showProgress(false);
    }

    @Override
    public void onLoaderReset(Loader<RedditData> loader) {

    }

    private void showProgress(boolean show) {
        mProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        mListView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        RedditData data = (RedditData) parent.getAdapter().getItem(position);
        if (data.isSelf) {
            SelfActivity.startSelfActivity(this, data);
        } else if (data.isImage()) {
            ImageActivity.startImageActivity(this, data);
        } else {
            WebActivity.startWebActivity(this, data);
        }
    }
    
    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        int pos = PreferenceManager.getDefaultSharedPreferences(this).getInt("pos", 0);
        Toast.makeText(this, ((WorstAdapter)parent.getAdapter()).getItem(pos).title, Toast.LENGTH_SHORT).show();
        return true;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                if(!TextUtils.isEmpty(s)) {
                    loaderId++;
                    getSupportLoaderManager().initLoader(loaderId, getLoaderArguments(s), MainActivity.this);
                }
                supportInvalidateOptionsMenu();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    // Choreographer.FrameCallback

    @Override
    public void doFrame(long timestampNanoseconds) {
        // Ideally, frame intervals will be exactly 1x the hardware interval,
        // and 2x means you definitely dropped one frame. So 1.5x is our point of comparison.
        double droppedFrameIntervalSeconds = hardwareFrameIntervalSeconds * 1.5;

        long frameIntervalNanoseconds = timestampNanoseconds - lastTimestampNanoseconds;
        if (0 < lastTimestampNanoseconds) {
            // Compare if we have received at least two frame callbacks
            double frameIntervalSeconds = frameIntervalNanoseconds / 1_000_000_000.0;
            if (droppedFrameIntervalSeconds < frameIntervalSeconds) {
                soundPool.play(tickSoundID, 1, 1, 1, 0, 1);

                int frameIntervalMilliseconds = (int) (frameIntervalSeconds * 1000);
                int hardwareFrameIntervalMilliseconds = (int) (hardwareFrameIntervalSeconds * 1000);

                StringBuilder message = new StringBuilder();
                message.append("Dropped frame: ");
                message.append(frameIntervalMilliseconds);
                message.append("ms, out of ");
                message.append(hardwareFrameIntervalMilliseconds);
                message.append("ms");

                Log.d("Geiger Counter", message.toString());
            }
        }

        lastTimestampNanoseconds = timestampNanoseconds;
        Choreographer.getInstance().postFrameCallback(this);
    }

}
