package com.dmr.ocrhoneywell;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText txtEmail;
    private EditText txtPassword;

    private CheckBox chkMostrarPassword;

    private Button btnIngresar;
    private Button btnCerrarApp;

    private ProgressBar progressLogin;

    private TextView txtOlvidePassword;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        txtEmail =
                findViewById(R.id.txtEmail);

        txtPassword =
                findViewById(R.id.txtPassword);

        chkMostrarPassword =
                findViewById(
                        R.id.chkMostrarPassword
                );

        btnIngresar =
                findViewById(R.id.btnIngresar);

        btnCerrarApp =
                findViewById(R.id.btnCerrarApp);

        progressLogin =
                findViewById(R.id.progressLogin);

        txtOlvidePassword =
                findViewById(
                        R.id.txtOlvidePassword
                );

        configurarMostrarPassword();

        btnIngresar.setOnClickListener(
                v -> iniciarSesion()
        );

        btnCerrarApp.setOnClickListener(
                v -> finishAffinity()
        );

        txtOlvidePassword.setOnClickListener(
                v -> abrirRecuperacionPassword()
        );

        FirebaseUser usuarioActual =
                auth.getCurrentUser();

        if (usuarioActual != null) {
            cargarPerfil(usuarioActual);
        }
    }

    private void configurarMostrarPassword() {

        chkMostrarPassword
                .setOnCheckedChangeListener(
                        (buttonView, marcado) -> {

                            if (marcado) {

                                txtPassword.setInputType(
                                        InputType.TYPE_CLASS_TEXT
                                                |
                                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                                );

                            } else {

                                txtPassword.setInputType(
                                        InputType.TYPE_CLASS_TEXT
                                                |
                                                InputType.TYPE_TEXT_VARIATION_PASSWORD
                                );
                            }

                            txtPassword.setSelection(
                                    txtPassword
                                            .getText()
                                            .length()
                            );
                        }
                );
    }

    private void abrirRecuperacionPassword() {

        Intent intent =
                new Intent(
                        LoginActivity.this,
                        RecuperarPasswordActivity.class
                );

        startActivity(intent);
    }

    private void iniciarSesion() {

        String email =
                txtEmail
                        .getText()
                        .toString()
                        .trim()
                        .toLowerCase();

        String password =
                txtPassword
                        .getText()
                        .toString();

        if (TextUtils.isEmpty(email)) {

            txtEmail.setError(
                    "Ingrese el correo"
            );

            txtEmail.requestFocus();

            return;
        }

        if (TextUtils.isEmpty(password)) {

            txtPassword.setError(
                    "Ingrese la contraseña"
            );

            txtPassword.requestFocus();

            return;
        }

        mostrarCarga(true);

        auth.signInWithEmailAndPassword(
                        email,
                        password
                )
                .addOnSuccessListener(
                        resultado ->
                                cargarPerfil(
                                        resultado.getUser()
                                )
                )
                .addOnFailureListener(
                        error -> {

                            mostrarCarga(false);

                            Toast.makeText(
                                    this,
                                    "No fue posible iniciar sesión. Verifique correo y contraseña.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    private void cargarPerfil(
            FirebaseUser usuario
    ) {

        if (usuario == null) {

            mostrarCarga(false);

            return;
        }

        mostrarCarga(true);

        db.collection("usuarios")
                .document(usuario.getUid())
                .get()
                .addOnSuccessListener(
                        documento ->
                                validarPerfil(
                                        usuario,
                                        documento
                                )
                )
                .addOnFailureListener(
                        error -> {

                            mostrarCarga(false);

                            auth.signOut();

                            Toast.makeText(
                                    this,
                                    "No fue posible cargar el perfil del usuario.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    private void validarPerfil(
            FirebaseUser usuario,
            DocumentSnapshot documento
    ) {

        if (!documento.exists()) {

            mostrarCarga(false);

            auth.signOut();

            Toast.makeText(
                    this,
                    "La cuenta no tiene un perfil registrado.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String estado =
                valor(
                        documento.getString(
                                "estado"
                        )
                );

        String rol =
                valor(
                        documento.getString(
                                "rol"
                        )
                );

        String empresaId =
                valor(
                        documento.getString(
                                "empresaId"
                        )
                );

        String empresaNombre =
                valor(
                        documento.getString(
                                "empresaNombre"
                        )
                );

        String nombre =
                valor(
                        documento.getString(
                                "nombre"
                        )
                );

        String apellido =
                valor(
                        documento.getString(
                                "apellido"
                        )
                );

        if (!"activo".equals(estado)) {

            mostrarCarga(false);

            auth.signOut();

            Toast.makeText(
                    this,
                    "La cuenta no está activa.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (
                !"operador".equals(rol)
                        &&
                        !"admin_empresa".equals(rol)
        ) {

            mostrarCarga(false);

            auth.signOut();

            Toast.makeText(
                    this,
                    "Este perfil no tiene acceso a la aplicación de visitas.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (empresaId.isEmpty()) {

            mostrarCarga(false);

            auth.signOut();

            Toast.makeText(
                    this,
                    "El usuario no tiene una empresa asignada.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        validarEmpresa(
                usuario.getUid(),
                nombre,
                apellido,
                rol,
                empresaId,
                empresaNombre
        );
    }


    private void validarEmpresa(
            String uid,
            String nombre,
            String apellido,
            String rol,
            String empresaId,
            String empresaNombre
    ) {

        db.collection("empresas")
                .document(empresaId)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(documento -> {

                    if (!documento.exists()) {

                        mostrarCarga(false);
                        auth.signOut();

                        Toast.makeText(
                                this,
                                "La empresa asignada no existe.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    Boolean activa =
                            documento.getBoolean("activa");

                    if (!Boolean.TRUE.equals(activa)) {

                        mostrarCarga(false);
                        auth.signOut();

                        Toast.makeText(
                                this,
                                "Empresa deshabilitada. Contacte al administrador.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    abrirAplicacion(
                            uid,
                            nombre,
                            apellido,
                            rol,
                            empresaId,
                            empresaNombre
                    );

                })
                .addOnFailureListener(error -> {

                    mostrarCarga(false);
                    auth.signOut();

                    Toast.makeText(
                            this,
                            "No fue posible verificar el estado de la empresa. Intente nuevamente.",
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    private void abrirAplicacion(
            String uid,
            String nombre,
            String apellido,
            String rol,
            String empresaId,
            String empresaNombre
    ) {

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );

        intent.putExtra(
                "uid",
                uid
        );

        intent.putExtra(
                "nombreUsuario",
                nombre
        );

        intent.putExtra(
                "apellidoUsuario",
                apellido
        );

        intent.putExtra(
                "rol",
                rol
        );

        intent.putExtra(
                "empresaId",
                empresaId
        );

        intent.putExtra(
                "empresaNombre",
                empresaNombre
        );

        startActivity(intent);

        finish();
    }

    private void mostrarCarga(
            boolean cargando
    ) {

        progressLogin.setVisibility(
                cargando
                        ? ProgressBar.VISIBLE
                        : ProgressBar.GONE
        );

        btnIngresar.setEnabled(
                !cargando
        );

        txtEmail.setEnabled(
                !cargando
        );

        txtPassword.setEnabled(
                !cargando
        );

        chkMostrarPassword.setEnabled(
                !cargando
        );
    }

    private String valor(
            String texto
    ) {

        return texto == null
                ? ""
                : texto.trim();
    }
}
