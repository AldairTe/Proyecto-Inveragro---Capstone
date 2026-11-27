package com.jazaldcorp.inveragrovendedores.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.jazaldcorp.inveragrovendedores.R;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Asistencia;
import com.jazaldcorp.inveragrovendedores.models.Usuario;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);


    }

}