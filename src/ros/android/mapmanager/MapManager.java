/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package ros.android.mapmanager;

import ros.android.activity.RosAppActivity;
import android.os.Bundle;
import org.ros.node.Node;
import android.util.Log;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.ros.node.Node;
import org.ros.node.service.ServiceResponseListener;
import org.ros.exception.RosException;
import org.ros.exception.RemoteException;
import org.ros.node.service.ServiceClient;
import org.ros.message.map_store.MapListEntry;
import org.ros.namespace.NameResolver;
import org.ros.service.map_store.ListMaps;
import org.ros.service.map_store.DeleteMap;
import org.ros.service.map_store.PublishMap;
import org.ros.service.map_store.RenameMap;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import android.app.ProgressDialog;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.EditText;
import android.view.KeyEvent;
import ros.android.views.MapView;
import ros.android.views.MapDisplay;

/**
 * @author damonkohler@google.com (Damon Kohler)
 * @author pratkanis@willowgarage.com (Tony Pratkanis)
 */
public class MapManager extends RosAppActivity implements MapDisplay.MapDisplayStateCallback {
  private ListView mapListView;
  private LinearLayout mapListLayout;
  private LinearLayout mapDetailLayout;
  private TextView mapNameView;
  private TextView mapLoadView;
  private ProgressDialog waitingDialog;
  private AlertDialog errorDialog;
  private String mapId;
  private String mapName;
  private MapView mapView;
  private static final int NAME_MAP_DIALOG_ID = 0;
  ArrayList<MapListEntry> mapList = new ArrayList<MapListEntry>();

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    setDefaultAppName("map_manager/map_manager");
    setDashboardResource(R.id.top_bar);
    setMainWindowResource(R.layout.main);
    super.onCreate(savedInstanceState);
    
    mapListView = (ListView)findViewById(R.id.map_list);
    mapListLayout = (LinearLayout)findViewById(R.id.map_list_view);
    mapDetailLayout = (LinearLayout)findViewById(R.id.map_detail_view);
    mapNameView = (TextView)findViewById(R.id.map_name_view);
    mapLoadView = (TextView)findViewById(R.id.map_load_view);
    
    mapView = (MapView) findViewById(R.id.map_view);
    mapView.addMapDisplayCallback(this);
    
    mapListLayout.setVisibility(mapListLayout.VISIBLE);
    mapDetailLayout.setVisibility(mapListLayout.GONE);
  }

  private void updateMapListGui(final ArrayList<MapListEntry> mapList) {
    // Make an array of map name/date strings.
    final String[] availableMapNames = new String[mapList.size()];
    for( int i = 0; i < mapList.size(); i++ ) {
      String displayString;
      String name = mapList.get(i).name;
      Date creationDate = new Date(mapList.get(i).date * 1000);
      String dateTime = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(creationDate);
      if( name != null && ! name.equals("") ) {
        displayString = name + " " + dateTime;
      } else {
        displayString = dateTime;
      }
      availableMapNames[i] = displayString;
    }

    MapManager.this.mapList = mapList;
    
    runOnUiThread(new Runnable() {
        @Override public void run() {
          ArrayAdapter ad = new ArrayAdapter(MapManager.this, android.R.layout.simple_list_item_1, availableMapNames);
          mapListView.setAdapter(ad);
          mapListView.setOnItemClickListener(new OnItemClickListener() {
              public void onItemClick(AdapterView adapter, View view, int index, long id) {
                Log.i("MapManager", MapManager.this.mapList.get(index).map_id);
                updateMapView(MapManager.this.mapList.get(index));
                mapListLayout.setVisibility(mapListLayout.GONE);
                mapDetailLayout.setVisibility(mapListLayout.VISIBLE);
              }});
        }});
  }
  
  @Override
  public void onMapDisplayState(final MapDisplay.State state) {
    if (state == MapDisplay.State.STATE_WORKING) {
      runOnUiThread(new Runnable() {
          @Override public void run() {
            mapView.setVisibility(mapView.VISIBLE);
            mapLoadView.setVisibility(mapLoadView.GONE);
          }});
    }
  }

  private void updateMapView(MapListEntry map) {
    mapNameView.setText(map.name);
    mapId = map.map_id;
    mapName = map.name;
    mapView.resetMapDisplayState();
    mapView.setVisibility(mapView.INVISIBLE);
    mapLoadView.setVisibility(mapLoadView.VISIBLE);
    try {
      ServiceClient<PublishMap.Request, PublishMap.Response> publishMapServiceClient =
        getNode().newServiceClient("publish_map", "map_store/PublishMap");
      PublishMap.Request req = new PublishMap.Request();
      req.map_id = map.map_id;
      publishMapServiceClient.call(req, new ServiceResponseListener<PublishMap.Response>() {
          @Override public void onSuccess(PublishMap.Response message) {
            //Don't really need to do anything.
          }
          @Override public void onFailure(RemoteException e) {
            e.printStackTrace();
            safeShowErrorDialog("Error loading map: " + e.toString());
          }
        });
    } catch (Exception e) {
      e.printStackTrace();
      safeShowErrorDialog("Error loading map: " + e.toString());
    }
  }

  public void renameMap(View view) {
    Log.e("MapManager", "rename");
    final String id = mapId;
    if (id == null) {
      return;
    }
    showDialog(NAME_MAP_DIALOG_ID);
  }

  public void returnToMain(View view) {
    mapId = null;
    mapName = null;
    mapListLayout.setVisibility(mapListLayout.VISIBLE);
    mapDetailLayout.setVisibility(mapListLayout.GONE);
  }
    
  public void deleteMap(final View view) {
    final String id = mapId;
    if (id == null) {
      return;
    }
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setTitle("Are You Sure?");
    dialog.setMessage("Are you sure you want to delete this map?");
    dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dlog, int i) {
        dlog.dismiss();
        safeShowWaitingDialog("Waiting for deletion...");
        try {
          ServiceClient<DeleteMap.Request, DeleteMap.Response> deleteMapServiceClient =
            getNode().newServiceClient("delete_map", "map_store/DeleteMap");
          DeleteMap.Request req = new DeleteMap.Request();
          req.map_id = id;
          deleteMapServiceClient.call(req, new ServiceResponseListener<DeleteMap.Response>() {
              @Override public void onSuccess(DeleteMap.Response message) {
                MapManager.this.runOnUiThread(new Runnable() {
                    public void run() {
                      safeDismissWaitingDialog();
                      returnToMain(view);
                      updateMapList();
                    }});
              }
              @Override public void onFailure(RemoteException e) {
                e.printStackTrace();
                safeShowErrorDialog("Error during map delete: " + e.toString());
              }});
        } catch (Exception e) {
          e.printStackTrace();
          safeShowErrorDialog("Error during map delete: " + e.toString());
        }
      }
    });
    dialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dlog, int i) {
        dlog.dismiss();
      }
    });
    dialog.show();
  }

  private void updateMapList() {
    updateMapList(0);
  }

  private void updateMapList(final int n) {
    try {
      safeShowWaitingDialog("Waiting for maps...");
      ServiceClient<ListMaps.Request, ListMaps.Response> listMapsServiceClient =
        getNode().newServiceClient("list_maps", "map_store/ListMaps");
      listMapsServiceClient.call(new ListMaps.Request(), new ServiceResponseListener<ListMaps.Response>() {
          @Override public void onSuccess(ListMaps.Response message) {
            Log.i("MapNav", "readAvailableMapList() Success");
            safeDismissWaitingDialog();
            updateMapListGui(message.map_list);
          }
          @Override public void onFailure(RemoteException e) {
            if (n < 30) {
              Log.i("MapManager", "Waiting for service");
              try {
                Thread.sleep(1000L);
                updateMapList(n + 1);
                return;
              } catch (Exception ex) {}
            }
            Log.i("MapManager", "readAvailableMapList() Failure");
            safeToastStatus("Reading map list failed: " + e.getMessage());
            safeDismissWaitingDialog();
            e.printStackTrace();
            safeShowErrorDialog("Error during list update: " + e.toString());
          }
        });
    } catch (Exception e) {
      if (n < 30) {
        Log.i("MapManager", "Waiting for service");
        try {
          Thread.sleep(1000L);
          updateMapList(n + 1);
          return;
        } catch (Exception ex) {}
      }
      e.printStackTrace();
      safeShowErrorDialog("Error during list update: " + e.toString());
    }
  }

  /** Called when the node is created */
  @Override
  protected void onNodeCreate(Node node) {
    super.onNodeCreate(node);
    updateMapList();
    try {
      mapView.start(node);
    } catch (RosException e) {
      e.printStackTrace();
      safeShowErrorDialog("Starting failed: " + e.toString());
    }
  }

  /** Called when the node is destroyed */
  @Override
  protected void onNodeDestroy(Node node) {
    super.onNodeDestroy(node);
    mapView.stop();
  }
  
  /** Creates the menu for the options */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.map_manager_menu, menu);
    return true;
  }

  /** Run when the menu is clicked. */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.kill: //Shutdown if the user clicks kill
      android.os.Process.killProcess(android.os.Process.myPid());
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
  }
  
  @Override
  protected Dialog onCreateDialog(int id) {
    Dialog dialog;
    Button button;
    switch (id) {
    case NAME_MAP_DIALOG_ID:
      dialog = new Dialog(this);
      dialog.setContentView(R.layout.name_map_dialog);
      dialog.setTitle("Rename Map");

      final String targetMapId = mapId;
      final EditText nameField = (EditText) dialog.findViewById(R.id.name_editor);
      nameField.setText(mapName);
      nameField.setOnKeyListener(new View.OnKeyListener() {
          @Override
          public boolean onKey(final View view, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
              String newName = nameField.getText().toString();
              if (newName != null && newName.length() > 0) {
                safeShowWaitingDialog("Waiting for rename...");
                try {
                  ServiceClient<RenameMap.Request, RenameMap.Response> renameMapServiceClient =
                    getNode().newServiceClient("rename_map", "map_store/RenameMap");
                  RenameMap.Request req = new RenameMap.Request();
                  req.map_id = targetMapId;
                  req.new_name = newName;
                  renameMapServiceClient.call(req, new ServiceResponseListener<RenameMap.Response>() {
                      @Override public void onSuccess(RenameMap.Response message) {
                        MapManager.this.runOnUiThread(new Runnable() {
                            public void run() {
                              safeDismissWaitingDialog();
                              returnToMain(view);
                              updateMapList();
                            }});
                      }
                      @Override public void onFailure(RemoteException e) {
                        e.printStackTrace();
                        safeShowErrorDialog("Error during rename: " + e.toString());
                      }});
                } catch (Exception e) {
                  e.printStackTrace();
                  safeShowErrorDialog("Error during rename: " + e.toString());
                }
              }
              removeDialog(NAME_MAP_DIALOG_ID);
              return true;
            } else {
              return false;
            }
          }
        });

      button = (Button) dialog.findViewById(R.id.cancel_button);
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          removeDialog(NAME_MAP_DIALOG_ID);
        }
      });
      break;
    default:
      dialog = null;
    }
    return dialog;
  }
  
  private void safeShowWaitingDialog(final CharSequence message) {
    runOnUiThread(new Runnable() {
        @Override public void run() {
          if( waitingDialog != null ) {
            waitingDialog.dismiss();
            waitingDialog = null;
          }
          waitingDialog = ProgressDialog.show(MapManager.this, "", message, true);
        }
      });
  }
  
  private void safeDismissWaitingDialog() {
    runOnUiThread(new Runnable() {
        @Override public void run() {
          if( waitingDialog != null ) {
            waitingDialog.dismiss();
            waitingDialog = null;
          }
        }
      });
  }

  private void safeShowErrorDialog(final CharSequence message) {
    runOnUiThread(new Runnable() {
        @Override public void run() {
          if( errorDialog != null ) {
            errorDialog.dismiss();
            errorDialog = null;
          }
          if( waitingDialog != null ) {
            waitingDialog.dismiss();
            waitingDialog = null;
          }
          AlertDialog.Builder dialog = new AlertDialog.Builder(MapManager.this);
          dialog.setTitle("Error");
          dialog.setMessage(message);
          dialog.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dlog, int i) {
                dlog.dismiss();
              }});
          errorDialog = dialog.show();
        }
      });
  }
  
  private void safeDismissErrorDialog() {
    runOnUiThread(new Runnable() {
        @Override public void run() {
          if( errorDialog != null ) {
            errorDialog.dismiss();
            errorDialog = null;
          }
        }
      });
  }
}
