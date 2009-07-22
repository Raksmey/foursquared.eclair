/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.types.Data;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.util.SeparatedListAdapter;
import com.joelapenna.foursquared.widget.TipListAdapter;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.AdapterView.OnItemClickListener;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ExecutionException;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
public class VenueTipsActivity extends ListActivity {
    public static final String TAG = "VenueTipsActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    private static final int DIALOG_TODO = 0;
    private static final int DIALOG_TIP = 1;
    private static final int DIALOG_ADD_FAIL_MESSAGE = 2;
    private static final int DIALOG_ADD_SHOW_MESSAGE = 3;
    private static final int DIALOG_UPDATE_FAIL_MESSAGE = 4;
    private static final int DIALOG_UPDATE_SHOW_MESSAGE = 5;

    private Venue mVenue;
    private Group mGroups;

    private AddAsyncTask mAddAsyncTask;
    private UpdateAsyncTask mUpdateAsyncTask;

    private Button mTipButton;
    private Button mTodoButton;
    private Observer mVenueObserver = new VenueObserver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.venue_tips_activity);

        setListAdapter(new SeparatedListAdapter(this));
        // TODO(jlapenna): Hey Joe, you need to make this work...
        // TODO(foursquare): Hey Foursquare, you need to support this in the API.
        /*
        getListView().setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (DEBUG) Log.d(TAG, "Click for: " + String.valueOf(position));
                CheckBox checkbox = (CheckBox)view.findViewById(R.id.checkbox);
                checkbox.setChecked(!checkbox.isChecked());
                Tip tip = (Tip)((SeparatedListAdapter)getListAdapter()).getItem(position);
                updateTodo(tip.getId());
            }
        });
        */

        setupUi();

        VenueActivity parent = (VenueActivity)getParent();
        if (parent.venueObservable.getVenue() != null) {
            mVenueObserver.update(parent.venueObservable, parent.venueObservable.getVenue());
        } else {
            parent.venueObservable.addObserver(mVenueObserver);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAddAsyncTask != null) {
            mAddAsyncTask.cancel(true);
        }
        if (mUpdateAsyncTask != null) {
            mUpdateAsyncTask.cancel(true);
        }
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
        String title = null;
        String message = null;
        TipTask task = null;
        switch (id) {
            case DIALOG_ADD_FAIL_MESSAGE:
                title = "Sorry!";
                message = "Could not add your " + mAddAsyncTask.type + " Try again!";
                break;
            case DIALOG_ADD_SHOW_MESSAGE:
                title = "Added!";
                task = mAddAsyncTask;
                break;
            case DIALOG_UPDATE_FAIL_MESSAGE:
                title = "Sorry!";
                message = "Could not update your " + mUpdateAsyncTask.type + " Try again!";
                break;
            case DIALOG_UPDATE_SHOW_MESSAGE:
                title = "Completed!";
                task = mUpdateAsyncTask;
                break;
            case DIALOG_TODO:
                title = "Add a Todo!";
                message = "I want to . . .";
                break;
            case DIALOG_TIP:
                title = "Add a Tip!";
                message = "I did this . . .";
                break;
        }

        if (message == null && task == null) {
            throw new RuntimeException("This should never happen no message or task!");
        } else if (message == null) {
            try {
                message = task.get().getMessage();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        AlertDialog alertDialog = (AlertDialog)dialog;
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (DEBUG) Log.d(TAG, "onCreateDialog: " + String.valueOf(id));
        DialogInterface.OnClickListener listener = null;

        final EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.WRAP_CONTENT));

        // Handle the simple result dialogs
        switch (id) {
            case DIALOG_TODO:
                listener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String text = editText.getText().toString();
                        if (!TextUtils.isEmpty(text)) {
                            addTodo(text);
                            editText.setText("");
                        }
                    }
                };
                break;

            case DIALOG_TIP:
                listener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String text = editText.getText().toString();
                        if (!TextUtils.isEmpty(text)) {
                            addTip(text);
                            editText.setText("");
                        }
                    }
                };
                break;
        }

        /*
         * if (message == null) { try { message = task.get().getMessage(); } catch
         * (InterruptedException e) { throw new RuntimeException(e); } catch (ExecutionException e)
         * { throw new RuntimeException(e); } }
         */

        if (listener == null) {
            return new AlertDialog.Builder(VenueTipsActivity.this) //
                    .setIcon(android.R.drawable.ic_dialog_info) //
                    .setTitle("This is a dummy title.") // Weird layout issues if this isn't called.
                    .setMessage("This is a dummy message.") // Weird layout issues if this isn't
                    // called.
                    .create();
        } else {
            return new AlertDialog.Builder(VenueTipsActivity.this) //
                    .setView(editText) //
                    .setIcon(android.R.drawable.ic_dialog_info) //
                    .setPositiveButton("Add", listener) //
                    .setTitle("This is a dummy title.") // Weird layout issues if this isn't called.
                    .setMessage("This is a dummy message.") // Weird layout issues if this isn't
                    // called.
                    .create();
        }
    }

    private void setupUi() {
        mTipButton = (Button)findViewById(R.id.tipButton);
        mTipButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(DIALOG_TIP);
            }
        });
        mTodoButton = (Button)findViewById(R.id.todoButton);
        mTodoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(DIALOG_TODO);
            }
        });
    }

    private Group onVenueSet(Venue venue) {
        Group tipsAndTodos = new Group();

        Group tips = venue.getTips();
        if (tips != null) {
            tips.setType("Tips");
            tipsAndTodos.add(tips);
        }

        tips = venue.getTodos();
        if (tips != null) {
            tips.setType("Todos");
            tipsAndTodos.add(tips);
        }
        return tipsAndTodos;
    }

    private void addTip(String text) {
        mAddAsyncTask = (AddAsyncTask)new AddAsyncTask().execute(AddAsyncTask.TIP, text);
    }

    private void addTodo(String text) {
        mAddAsyncTask = (AddAsyncTask)new AddAsyncTask().execute(AddAsyncTask.TODO, text);
    }

    private void updateTodo(String todoid) {
        mUpdateAsyncTask = (UpdateAsyncTask)new UpdateAsyncTask().execute(TipTask.TODO, todoid);
    }

    private void setTipGroups(Group groups) {
        mGroups = groups;
        putGroupsInAdapter(mGroups);
    }

    private void putGroupsInAdapter(Group groups) {
        SeparatedListAdapter mainAdapter = (SeparatedListAdapter)getListAdapter();
        mainAdapter.clear();
        int groupCount = groups.size();
        for (int groupsIndex = 0; groupsIndex < groupCount; groupsIndex++) {
            Group group = (Group)groups.get(groupsIndex);
            TipListAdapter groupAdapter = new TipListAdapter(this, group);
            mainAdapter.addSection(group.getType(), groupAdapter);
        }
        mainAdapter.notifyDataSetInvalidated();
    }

    private final class VenueObserver implements Observer {
        @Override
        public void update(Observable observable, Object data) {
            mVenue = (Venue)data;
            setTipGroups(onVenueSet(mVenue));
            findViewById(R.id.tipButton).setEnabled(true);
            findViewById(R.id.todoButton).setEnabled(true);
        }
    }

    private class TipTask extends AsyncTask<String, Void, Data> {

        public static final String TODO = "todo";
        public static final String TIP = "tip";

        @Override
        protected Data doInBackground(String... params) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    private class AddAsyncTask extends TipTask {

        private static final String PROGRESS_BAR_TASK_ID = TAG + "AddAsyncTask";

        String type = null;

        @Override
        public void onPreExecute() {
            if (DEBUG) Log.d(TAG, "AddAsyncTask: onPreExecute()");
            ((VenueActivity)getParent()).startProgressBar(PROGRESS_BAR_TASK_ID);
        }

        @Override
        public Data doInBackground(String... params) {
            assert params.length == 2;
            this.type = (String)params[0];
            String text = (String)params[1];

            try {
                Foursquare foursquare = Foursquared.getFoursquare();
                return foursquare.addTip(mVenue.getId(), text, this.type);
            } catch (FoursquareException e) {
                // TODO Auto-generated catch block
                if (DEBUG) Log.d(TAG, "FoursquareException", e);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                if (DEBUG) Log.d(TAG, "IOException", e);
            }
            return null;
        }

        @Override
        public void onPostExecute(Data result) {
            if (DEBUG) Log.d(TAG, "AddAsyncTask: onPostExecute: " + result);
            if (result == null) {
                showDialog(DIALOG_ADD_FAIL_MESSAGE);
            } else {
                showDialog(DIALOG_ADD_SHOW_MESSAGE);
            }
            ((VenueActivity)getParent()).stopProgressBar(PROGRESS_BAR_TASK_ID);
        }

        @Override
        public void onCancelled() {
            ((VenueActivity)getParent()).stopProgressBar(PROGRESS_BAR_TASK_ID);
        }
    }

    private class UpdateAsyncTask extends TipTask {

        private static final String PROGRESS_BAR_TASK_ID = TAG + "UpdateAsyncTask";

        String type = null;

        @Override
        public void onPreExecute() {
            if (DEBUG) Log.d(TAG, "UpdateAsyncTask: onPreExecute()");
            ((VenueActivity)getParent()).startProgressBar(PROGRESS_BAR_TASK_ID);
        }

        @Override
        public Data doInBackground(String... params) {
            assert params.length == 1;
            type = (String)params[0];
            String tipid = (String)params[1];

            try {
                Foursquare foursquare = Foursquared.getFoursquare();
                return foursquare.update("done", tipid);
            } catch (FoursquareException e) {
                // TODO Auto-generated catch block
                if (DEBUG) Log.d(TAG, "FoursquareException", e);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                if (DEBUG) Log.d(TAG, "IOException", e);
            }
            return null;
        }

        @Override
        public void onPostExecute(Data result) {
            if (DEBUG) Log.d(TAG, "UpdateAsyncTask: onPostExecute: " + result);
            if (result == null) {
                showDialog(DIALOG_UPDATE_FAIL_MESSAGE);
            } else {
                showDialog(DIALOG_UPDATE_SHOW_MESSAGE);
            }
            ((VenueActivity)getParent()).stopProgressBar(PROGRESS_BAR_TASK_ID);
        }

        @Override
        public void onCancelled() {
            ((VenueActivity)getParent()).stopProgressBar(PROGRESS_BAR_TASK_ID);
        }
    }
}