package com.cappielloantonio.tempo.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.ui.adapter.ServerAdapter;
import com.cappielloantonio.tempo.databinding.FragmentLoginBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.interfaces.SystemCallback;
import com.cappielloantonio.tempo.model.Server;
import com.cappielloantonio.tempo.repository.SystemRepository;
import com.cappielloantonio.tempo.subsonic.api.navidrome.NavidromeClient;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.BackupRestoreFlow;
import com.cappielloantonio.tempo.ui.dialog.ServerSignupDialog;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.LoginViewModel;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class LoginFragment extends Fragment implements ClickCallback {
    private static final String TAG = "LoginFragment";

    private FragmentLoginBinding bind;
    private MainActivity activity;
    private LoginViewModel loginViewModel;

    private ServerAdapter serverAdapter;

    private static final String STATE_AFTER_RESTORE = "after_restore";
    private static final String STATE_LOGIN_SERVER = "restore_login_server";
    private static final String STATE_KNOWN_SERVERS = "restore_known_servers";
    private static final String SIGNUP_TAG = "ServerSignupDialog";

    private ActivityResultLauncher<String[]> restoreLauncher;
    /** Set by a restore, until the login it leads to: kept across the activity restart it needs. */
    private boolean afterRestore;
    /** The backup's own server, when its login came with it. */
    @Nullable
    private String restoreLoginServerId;
    /** Without a login in the backup: the servers there were before asking for one. */
    @Nullable
    private ArrayList<String> knownServerIds;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        restoreLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) BackupRestoreFlow.start(this, uri, this::onBackupRestored);
        });

        if (savedInstanceState != null) {
            afterRestore = savedInstanceState.getBoolean(STATE_AFTER_RESTORE);
            restoreLoginServerId = savedInstanceState.getString(STATE_LOGIN_SERVER);
            knownServerIds = savedInstanceState.getStringArrayList(STATE_KNOWN_SERVERS);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_AFTER_RESTORE, afterRestore);
        outState.putString(STATE_LOGIN_SERVER, restoreLoginServerId);
        outState.putStringArrayList(STATE_KNOWN_SERVERS, knownServerIds);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.login_page_menu, menu);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        loginViewModel = new ViewModelProvider(requireActivity()).get(LoginViewModel.class);
        bind = FragmentLoginBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        initAppBar();
        initServerListView();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if ((bind.serverInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.login_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    private void initServerListView() {
        bind.serverListRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.serverListRecyclerView.setHasFixedSize(true);

        serverAdapter = new ServerAdapter(this);
        bind.serverListRecyclerView.setAdapter(serverAdapter);
        loginViewModel.getServerList().observe(getViewLifecycleOwner(), servers -> {
            if (!servers.isEmpty()) {
                if (bind != null) bind.noServerAddedTextView.setVisibility(View.GONE);
                if (bind != null) bind.serverListRecyclerView.setVisibility(View.VISIBLE);
                serverAdapter.setItems(servers);
            } else {
                if (bind != null) bind.noServerAddedTextView.setVisibility(View.VISIBLE);
                if (bind != null) bind.serverListRecyclerView.setVisibility(View.GONE);
            }
            if (afterRestore) continueAfterRestore(servers);
        });
    }

    private void onBackupRestored(int restored, @Nullable String loginServerId) {
        afterRestore = true;
        restoreLoginServerId = loginServerId;
        knownServerIds = null;
        BackupRestoreFlow.applyRestoredSettings(requireActivity());
        // Theme, colours and language only apply when the activity starts.
        requireActivity().recreate();
    }

    /** Logs into the backup's server, or asks for a login and then logs into the server added. */
    private void continueAfterRestore(List<Server> servers) {
        if (restoreLoginServerId != null) {
            for (Server server : servers) {
                if (server.getServerId().equals(restoreLoginServerId)) {
                    afterRestore = false;
                    logIn(server);
                    return;
                }
            }
        }

        if (knownServerIds == null) {
            knownServerIds = new ArrayList<>();
            for (Server server : servers) knownServerIds.add(server.getServerId());
            if (activity.getSupportFragmentManager().findFragmentByTag(SIGNUP_TAG) == null) {
                Toast.makeText(requireContext(), R.string.login_restore_add_server, Toast.LENGTH_LONG).show();
                new ServerSignupDialog().show(activity.getSupportFragmentManager(), SIGNUP_TAG);
            }
            return;
        }

        for (Server server : servers) {
            if (!knownServerIds.contains(server.getServerId())) {
                afterRestore = false;
                logIn(server);
                return;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_restore) {
            // Some file providers label JSON as plain text or octet-stream.
            restoreLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
            return true;
        }

        if (item.getItemId() == R.id.action_add) {
            ServerSignupDialog dialog = new ServerSignupDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        }

        return false;
    }

    @Override
    public void onServerClick(Bundle bundle) {
        logIn(bundle.getParcelable("server_object"));
    }

    private void logIn(Server server) {
        String password = Preferences.getPasswordForServer(server.getServerId());
        saveServerPreference(server.getServerId(), server.getAddress(), server.getLocalAddress(), server.getUsername(), password, server.isLowSecurity());

        SystemRepository systemRepository = new SystemRepository();
        systemRepository.checkUserCredential(new SystemCallback() {
            @Override
            public void onError(Exception exception) {
                Preferences.switchInUseServerAddress();
                resetServerPreference();
                Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(String password, String token, String salt) {
                activity.goFromLogin();
            }
        });
    }

    @Override
    public void onServerLongClick(Bundle bundle) {
        ServerSignupDialog dialog = new ServerSignupDialog();
        dialog.setArguments(bundle);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    private void saveServerPreference(String serverId, String server, String localAddress, String user, String password, boolean isLowSecurity) {
        Preferences.setServerId(serverId);
        Preferences.setServer(server);
        Preferences.setLocalAddress(localAddress);
        Preferences.setUser(user);
        Preferences.setPassword(password);
        Preferences.setLowSecurity(isLowSecurity);

        App.getSubsonicClientInstance(true);
    }

    private void resetServerPreference() {
        Preferences.setServerId(null);
        Preferences.setServer(null);
        Preferences.setUser(null);
        Preferences.setPassword(null);
        Preferences.setToken(null);
        Preferences.setSalt(null);
        Preferences.setLowSecurity(false);

        NavidromeClient.clearCredentials();
        App.getSubsonicClientInstance(true);
    }
}
