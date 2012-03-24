package com.nolanlawson.keepscore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.nolanlawson.keepscore.data.SavedGameAdapter;
import com.nolanlawson.keepscore.data.SeparatedListAdapter;
import com.nolanlawson.keepscore.data.TimePeriod;
import com.nolanlawson.keepscore.db.Game;
import com.nolanlawson.keepscore.db.GameDBHelper;
import com.nolanlawson.keepscore.util.StringUtil;
import com.nolanlawson.keepscore.util.UtilLogger;

public class LoadGameActivity extends ListActivity implements OnItemLongClickListener {

	private static UtilLogger log = new UtilLogger(LoadGameActivity.class);
	
	private SeparatedListAdapter adapter;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setListAdapter(adapter);
        
        setContentView(R.layout.load_game);
        
        getListView().setOnItemLongClickListener(this);
	}
	
	

	@Override
	protected void onResume() {
		super.onResume();
		
        List<Game> games = getAllGames();
        Collections.sort(games, Game.byRecentlySaved());
        log.d("loaded games %s", games);
        
        SortedMap<TimePeriod, List<Game>> organizedGames = organizeGamesByTimePeriod(games);
        
        adapter = new SeparatedListAdapter(this);
        for (Entry<TimePeriod, List<Game>> entry : organizedGames.entrySet()) {
        	TimePeriod timePeriod = entry.getKey();
        	List<Game> gamesSection = entry.getValue();
        	SavedGameAdapter subAdapter = new SavedGameAdapter(this, gamesSection);
        	adapter.addSection(getString(timePeriod.getTitleResId()), subAdapter);
        }
        setListAdapter(adapter);
	}



	private List<Game> getAllGames() {
		GameDBHelper dbHelper = null;
		try {
			dbHelper = new GameDBHelper(this);
			return dbHelper.findAllGames();
		} finally {
			if (dbHelper != null) {
				dbHelper.close();
			}
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		
		Game game = (Game) adapter.getItem(position);
		
		Intent intent = new Intent(this, GameActivity.class);
		intent.putExtra(GameActivity.EXTRA_GAME_ID, game.getId());
		
		startActivity(intent);
		
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long id) {
		
		showOptionsMenu((Game)(this.adapter.getItem(position)));
		
		return true;
	}

	private void showOptionsMenu(final Game game) {
		
		String editTitle = getString(TextUtils.isEmpty(game.getName()) 
				? R.string.title_name_game 
				: R.string.title_edit_game_name);
		
		CharSequence[] options = new CharSequence[]{
				getString(R.string.text_delete), 
				getString(R.string.text_copy),
				getString(R.string.menu_history),
				editTitle
				};
		
		new AlertDialog.Builder(this)
			.setCancelable(true)
			.setItems(options, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					
					switch (which) {
					case 0: // delete
						showDeleteDialog(game);
						break;
					case 1: // copy
						copyGame(game);
						break;
					case 2: // history
						showHistory(game);
						break;
					case 3: // edit name
						showEditGameNameDialog(game);
						break;	
					}
				}
			})
			.show();
		
	}

	private void copyGame(Game game) {

		final Game newGame = game.makeCleanCopy();
		
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				GameDBHelper dbHelper = null;
				try {
					dbHelper = new GameDBHelper(LoadGameActivity.this);
					dbHelper.saveGame(newGame);
				} finally {
					if (dbHelper != null) {
						dbHelper.close();
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				Toast.makeText(LoadGameActivity.this, R.string.toast_game_copied, Toast.LENGTH_SHORT).show();
				
				// if the "recent" group doesn't exist, need to create it
				if (!adapter.getSection(0).equals(getString(TimePeriod.values()[0].getTitleResId()))) {
					adapter.addSection(getString(TimePeriod.values()[0].getTitleResId()), 
							new SavedGameAdapter(LoadGameActivity.this, 
									new ArrayList<Game>(Collections.singleton(newGame))));
				} else { // just insert it into the first section
					((SavedGameAdapter)adapter.getSection(0)).insert(newGame, 0);
				}
				adapter.notifyDataSetChanged();
			}
			
			
			
		}.execute((Void)null);
	}



	private void showHistory(Game game) {
		
		Intent intent = new Intent(this, HistoryActivity.class);
		intent.putExtra(HistoryActivity.EXTRA_GAME, game);
		
		startActivity(intent);
		
	}

	private void showEditGameNameDialog(final Game game) {

		final EditText editText = new EditText(this);
		editText.setHint(R.string.hint_game_name);
		editText.setText(StringUtil.nullToEmpty(game.getName()));
		editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		editText.setSingleLine();
		new AlertDialog.Builder(this)
			.setTitle(R.string.title_edit_game_name)
			.setView(editText)
			.setCancelable(true)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(final DialogInterface dialog, int which) {

					final String newName = StringUtil.nullToEmpty(editText.getText().toString());
					
					// update database in the background to avoid jankiness
					new AsyncTask<Void, Void, Void>(){

						@Override
						protected Void doInBackground(Void... params) {
							GameDBHelper dbHelper = null;
							try {
								dbHelper = new GameDBHelper(LoadGameActivity.this);
								dbHelper.updateGameName(game, newName);
							} finally {
								if (dbHelper != null) {
									dbHelper.close();
								}
							}
							return null;
						}

						@Override
						protected void onPostExecute(Void result) {
							super.onPostExecute(result);
							
							
							game.setName(newName.trim());
							adapter.notifyDataSetChanged();
							
							dialog.dismiss();
						}
						
						
						
					}.execute((Void)null);
					
					
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();

		
	}
	
	
	private void showDeleteDialog(final Game game) {
		new AlertDialog.Builder(this)
			.setCancelable(true)
			.setTitle(R.string.title_confirm)
			.setMessage(R.string.text_game_will_be_deleted)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					deleteGame(game);
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}
	private void deleteGame(final Game game) {
		
		// do in background to avoid jankiness
		new AsyncTask<Void, Void, Void>() {
			
			@Override
			protected Void doInBackground(Void... params) {
				
				
				GameDBHelper dbHelper = null;
				try {
					dbHelper = new GameDBHelper(LoadGameActivity.this);
					dbHelper.deleteGame(game);
					
				} finally {
					if (dbHelper != null) {
						dbHelper.close();
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				
				Toast.makeText(LoadGameActivity.this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
				for (Entry<String, BaseAdapter> entry : adapter.getSections().entrySet()) {
					SavedGameAdapter subAdapter = (SavedGameAdapter) entry.getValue();
					subAdapter.remove(game);
				}
				adapter.notifyDataSetChanged();
			}
			
		}.execute((Void)null);
	}
	
	private SortedMap<TimePeriod, List<Game>> organizeGamesByTimePeriod(List<Game> games) {
		SortedMap<TimePeriod, List<Game>> result = new TreeMap<TimePeriod, List<Game>>();

		Iterator<TimePeriod> timePeriodIterator = Arrays.asList(TimePeriod.values()).iterator();
		TimePeriod timePeriod = timePeriodIterator.next();
		Date date = new Date();
		for (Game game : games) {
			// time periods are sorted from newest to oldest, just like the games.  So we can just walk through
			// them in order
			while (!timePeriodMatches(date, timePeriod, game)) {
				timePeriod = timePeriodIterator.next();
			}
			List<Game> existing = result.get(timePeriod);
			if (existing == null) {
				result.put(timePeriod, new ArrayList<Game>(Collections.singleton(game)));
			} else {
				existing.add(game);
			}
		}
		return result;
	}


	/**
	 * Return true if the game occurred within this time period.
	 * @param date
	 * @param timePeriod
	 * @param currentGame
	 * @return
	 */
	private boolean timePeriodMatches(Date date, TimePeriod timePeriod, Game currentGame) {
		Date start = timePeriod.getStartDateFunction().apply(date);
		Date end = timePeriod.getEndDateFunction().apply(date);
		
		return currentGame.getDateSaved() < end.getTime() && currentGame.getDateSaved() >= start.getTime();
	}
}
