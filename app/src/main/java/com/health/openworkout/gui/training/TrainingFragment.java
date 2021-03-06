/*
 * Copyright (C) 2020 olie.xdev <olie.xdev@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.health.openworkout.gui.training;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.health.openworkout.R;
import com.health.openworkout.core.OpenWorkout;
import com.health.openworkout.core.datatypes.TrainingPlan;
import com.health.openworkout.core.datatypes.User;
import com.health.openworkout.core.datatypes.WorkoutItem;
import com.health.openworkout.core.datatypes.WorkoutSession;
import com.health.openworkout.core.datatypes.GitHubFile;
import com.health.openworkout.core.utils.PackageUtils;
import com.health.openworkout.gui.datatypes.GenericAdapter;
import com.health.openworkout.gui.datatypes.GenericFragment;
import com.health.openworkout.gui.datatypes.GenericSettingsFragment;
import com.health.openworkout.gui.utils.FileDialogHelper;

import java.util.List;

import timber.log.Timber;

public class TrainingFragment extends GenericFragment {
    private RecyclerView trainingsView;
    private FloatingActionButton importButton;

    private List<TrainingPlan> trainingPlanList;
    private TrainingPlan exportTrainingPlan;

    private TrainingsAdapter trainingsAdapter;
    private FileDialogHelper fileDialogHelper;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_training, container, false);

        fileDialogHelper = new FileDialogHelper(this);

        trainingsView = root.findViewById(R.id.trainingsView);

        trainingsView.setHasFixedSize(true);
        trainingsView.setLayoutManager(new LinearLayoutManager(getContext()));

        importButton = root.findViewById(R.id.importButton);
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavDirections action = TrainingFragmentDirections.actionNavTrainingsFragmentToNavTrainingsDatabaseFragment();
                Navigation.findNavController(getActivity(), R.id.nav_host_fragment).navigate(action);
            }
        });

        loadFromDatabase();

        return root;
    }

    @Override
    protected String getTitle() {
        return getString(R.string.label_training_plans);
    }

    @Override
    protected void loadFromDatabase() {
        trainingPlanList = OpenWorkout.getInstance().getTrainingPlans();

        trainingsAdapter = new TrainingsAdapter(getContext(), trainingPlanList);
        trainingsAdapter.setMode(getMode());
        trainingsView.setAdapter(trainingsAdapter);
    }

    @Override
    protected void saveToDatabase() {
        for (int i=0; i<trainingPlanList.size(); i++) {
            trainingPlanList.get(i).setOrderNr(i);
            OpenWorkout.getInstance().updateTrainingPlan(trainingPlanList.get(i));
        }
    }

    @Override
    protected GenericAdapter getAdapter() {
        return trainingsAdapter;
    }

    @Override
    protected RecyclerView getRecyclerView() {
        return trainingsView;
    }

    @Override
    protected List getItemList() {
        return trainingPlanList;
    }

    @Override
    protected void onSelectCallback(int position) {
        TrainingPlan trainingPlan = trainingPlanList.get(position);

        TrainingFragmentDirections.ActionTrainingFragmentToSessionFragment action = TrainingFragmentDirections.actionTrainingFragmentToSessionFragment();
        action.setTitle(trainingPlan.getName());
        action.setTrainingPlanId(trainingPlan.getTrainingPlanId());
        Navigation.findNavController(getActivity(), R.id.nav_host_fragment).navigate(action);
    }

    @Override
    protected void onEditCallback(int position) {
        TrainingPlan trainingPlan = trainingPlanList.get(position);

        TrainingFragmentDirections.ActionTrainingFragmentToTrainingSettingsFragment action = TrainingFragmentDirections.actionTrainingFragmentToTrainingSettingsFragment();
        action.setTrainingPlanId(trainingPlan.getTrainingPlanId());
        action.setMode(GenericSettingsFragment.SETTING_MODE.EDIT);
        action.setTitle(getString(R.string.label_edit));
        Navigation.findNavController(getActivity(), R.id.nav_host_fragment).navigate(action);
    }

    @Override
    protected void onDeleteCallback(int position) {
        User user = OpenWorkout.getInstance().getCurrentUser();
        long userTrainingPlanId = user.getTrainingsPlanId();
        TrainingPlan trainingPlanToBeDelete = trainingPlanList.get(position);

        if (userTrainingPlanId == trainingPlanToBeDelete.getTrainingPlanId()) {
            user.setTrainingsPlanId(-1);
            OpenWorkout.getInstance().updateUser(user);
        }

        Toast.makeText(getContext(), String.format(getString(R.string.label_delete_toast), trainingPlanToBeDelete.getName()), Toast.LENGTH_SHORT).show();
        OpenWorkout.getInstance().deleteTrainingPlan(trainingPlanList.get(position));
        trainingPlanList.remove(position);
    }

    @Override
    protected void onDuplicateCallback(int position) {
        TrainingPlan origTrainingPlan = trainingPlanList.get(position);
        TrainingPlan duplicateTrainingPlan = origTrainingPlan.clone();

        duplicateTrainingPlan.setTrainingPlanId(0);
        trainingPlanList.add(position, duplicateTrainingPlan);
        saveToDatabase();

        long trainingPlanId = OpenWorkout.getInstance().insertTrainingPlan(duplicateTrainingPlan);
        duplicateTrainingPlan.setTrainingPlanId(trainingPlanId);
    }

    @Override
    protected void onAddClick() {
        TrainingFragmentDirections.ActionTrainingFragmentToTrainingSettingsFragment action = TrainingFragmentDirections.actionTrainingFragmentToTrainingSettingsFragment();
        action.setMode(GenericSettingsFragment.SETTING_MODE.ADD);
        action.setTitle(getString(R.string.label_add));
        Navigation.findNavController(getActivity(), R.id.nav_host_fragment).navigate(action);
    }

    @Override
    protected void onResetClick() {
        for (TrainingPlan trainingPlan : trainingPlanList) {
            trainingPlan.setCountFinishedTraining(0);

            for (WorkoutSession workoutSession : trainingPlan.getWorkoutSessions()) {
                workoutSession.setFinished(false);

                for (WorkoutItem workoutItem : workoutSession.getWorkoutItems()) {
                    workoutItem.setFinished(false);
                    OpenWorkout.getInstance().updateWorkoutItem(workoutItem);
                }

                OpenWorkout.getInstance().updateWorkoutSession(workoutSession);
            }

            OpenWorkout.getInstance().updateTrainingPlan(trainingPlan);
        }
    }

    @Override
    protected void onExportClick(int position) {
        exportTrainingPlan = trainingPlanList.get(position);
        fileDialogHelper.openExportFileDialog(exportTrainingPlan.getName());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        fileDialogHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (fileDialogHelper.onActivityResult(requestCode, resultCode, data)) {
            Uri uri = data.getData();

            PackageUtils packageUtils = new PackageUtils(getContext());

            packageUtils.exportTrainingPlan(exportTrainingPlan, uri);
        }
    }
}
