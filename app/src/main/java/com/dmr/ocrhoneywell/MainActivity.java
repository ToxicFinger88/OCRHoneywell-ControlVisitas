package com.dmr.ocrhoneywell;

import android.app.AlertDialog;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {


    private static final String CSV_HEADER =
            "cedula,fecha_nacimiento,apellido1,apellido2,nombre,empresa,persona_visitar,fecha_lectura,hora_lectura\n";
    private FirebaseFirestore db;


    private String usuarioUid = "";
    private String usuarioNombre = "";
    private String usuarioRol = "";
    private String empresaId = "";
    private String empresaNombre = "";

    private String usuarioApellido = "";
    private TextView txtUsuarioLogueado;
    private TextView txtRolUsuario;

    private EditText txtCedula, txtFecha, txtApellido1, txtApellido2, txtNombre;
    private EditText txtEmpresa, txtPersonaVisitar, txtFechaRegistro, txtHoraRegistro;
    private Button btnGuardar, btnLimpiar, btnSalir, btnMenu, btnModoManual;

    private final StringBuilder bufferOCR = new StringBuilder();

    private boolean modoManual = false;
    private boolean editandoFecha = false;
    private boolean editandoTexto = false;

    private static final int REQUEST_EXPORTAR_CSV = 1001;
    private static final int REQUEST_EXPORTAR_XLS = 1002;


    private static final String DISPOSITIVO = "tablet-ocr-01";



    private final Handler relojHandler = new Handler(Looper.getMainLooper());

    private final Runnable relojRunnable = new Runnable() {
        @Override
        public void run() {
            actualizarFechaHora();
            relojHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();


        usuarioUid = getIntent().getStringExtra("uid");
        usuarioNombre = getIntent().getStringExtra("nombreUsuario");

        usuarioApellido =
                getIntent()
                        .getStringExtra(
                                "apellidoUsuario"
                        );


        usuarioRol = getIntent().getStringExtra("rol");
        empresaId = getIntent().getStringExtra("empresaId");
        empresaNombre = getIntent().getStringExtra("empresaNombre");

        if (usuarioUid == null) usuarioUid = "";
        if (usuarioNombre == null) usuarioNombre = "";

        if (usuarioApellido == null) {
            usuarioApellido = "";
        }

        if (usuarioRol == null) usuarioRol = "";
        if (empresaId == null) empresaId = "";
        if (empresaNombre == null) empresaNombre = "";

        /*
         * SEGURIDAD MULTIEMPRESA
         *
         * MainActivity nunca debe trabajar sin una empresa asignada.
         * LoginActivity ya valida este dato, pero repetimos la comprobación
         * aquí para evitar escrituras o consultas sin contexto empresarial.
         */
        if (empresaId.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "No se pudo identificar la empresa del usuario.",
                    Toast.LENGTH_LONG
            ).show();

            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(
                    MainActivity.this,
                    LoginActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();
            return;
        }


        txtCedula = findViewById(R.id.txtCedula);
        txtFecha = findViewById(R.id.txtFecha);
        txtApellido1 = findViewById(R.id.txtApellido1);
        txtApellido2 = findViewById(R.id.txtApellido2);
        txtNombre = findViewById(R.id.txtNombre);
        txtEmpresa = findViewById(R.id.txtEmpresa);
        txtPersonaVisitar = findViewById(R.id.txtPersonaVisitar);
        txtFechaRegistro = findViewById(R.id.txtFechaRegistro);
        txtHoraRegistro = findViewById(R.id.txtHoraRegistro);

        btnGuardar = findViewById(R.id.btnGuardar);
        btnLimpiar = findViewById(R.id.btnLimpiar);
        btnSalir = findViewById(R.id.btnSalir);
        btnMenu = findViewById(R.id.btnMenu);


        txtUsuarioLogueado =
                findViewById(
                        R.id.txtUsuarioLogueado
                );

        txtRolUsuario =
                findViewById(
                        R.id.txtRolUsuario
                );

        String nombreCompleto =
                (
                        usuarioNombre
                                + " "
                                + usuarioApellido
                ).trim();

        if (nombreCompleto.isEmpty()) {

            txtUsuarioLogueado.setText(
                    "Usuario"
            );

        } else {

            txtUsuarioLogueado.setText(
                    nombreCompleto
            );
        }

        if ("admin_empresa".equals(usuarioRol)) {

            txtRolUsuario.setText(
                    "Administrador"
            );

            btnMenu.setVisibility(
                    android.view.View.VISIBLE
            );

        } else {

            txtRolUsuario.setText(
                    "Usuario"
            );

            btnMenu.setVisibility(
                    android.view.View.GONE
            );
        }


        btnModoManual = findViewById(R.id.btnModoManual);

        setCamposOcrEditables(false);
        actualizarFechaHora();

        btnGuardar.setOnClickListener(v -> mostrarConfirmacionGuardar());
        btnLimpiar.setOnClickListener(v -> limpiarCampos());


        btnSalir.setOnClickListener(v -> {

            FirebaseAuth
                    .getInstance()
                    .signOut();

            Intent intent =
                    new Intent(
                            MainActivity.this,
                            LoginActivity.class
                    );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
        });


        btnMenu.setOnClickListener(v -> mostrarMenuConfiguracion());

        configurarLimitesCampos();
        configurarMascaraFecha();
        configurarCapitalizacionManual();


        btnModoManual.setOnClickListener(v -> alternarModoManual());
        activarModoOCR();

        relojHandler.post(relojRunnable);

    }

    private void configurarCapitalizacionManual() {
        aplicarCapitalizacion(txtNombre);
        aplicarCapitalizacion(txtApellido1);
        aplicarCapitalizacion(txtApellido2);
        aplicarCapitalizacion(txtPersonaVisitar);
    }

    private void aplicarCapitalizacion(EditText campo) {
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (editandoTexto) return;

                editandoTexto = true;

                String texto = s.toString();
                String formateado = capitalizarPalabras(texto);

                if (!texto.equals(formateado)) {
                    campo.setText(formateado);
                    campo.setSelection(campo.getText().length());
                }

                editandoTexto = false;
            }
        });
    }


    private String capitalizarPalabras(String texto) {
        texto = texto.toLowerCase(Locale.ROOT);

        StringBuilder resultado = new StringBuilder();
        boolean nuevaPalabra = true;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);

            if (Character.isLetter(c)) {
                if (nuevaPalabra) {
                    resultado.append(Character.toUpperCase(c));
                    nuevaPalabra = false;
                } else {
                    resultado.append(c);
                }
            } else {
                resultado.append(c);
                nuevaPalabra = c == ' ';
            }
        }

        return resultado.toString();
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (modoManual) {
            return super.dispatchKeyEvent(event);
        }

        if (event.getAction() != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event);

        int keyCode = event.getKeyCode();

        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            procesarLectura(bufferOCR.toString());
            bufferOCR.setLength(0);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_TAB) {
            bufferOCR.append("\t");
            return true;
        }

        char c = (char) event.getUnicodeChar();
        if (c != 0) {
            bufferOCR.append(c);
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private void procesarLectura(String lectura) {
        limpiarCamposSinMensaje();
        setCamposOcrEditables(false);

        String[] partes = lectura.split("\\t");

        if (partes.length >= 5) {
            txtCedula.setText(partes[0].trim());
            txtFecha.setText(formatearFechaNacimiento(partes[1].trim()));
            txtApellido1.setText(capitalizar(partes[2].trim()));
            txtApellido2.setText(capitalizar(partes[3].trim()));
            txtNombre.setText(capitalizar(partes[4].trim()));
            actualizarFechaHora();

            Toast.makeText(this, "Lectura OCR procesada", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Formato OCR incompleto. Puede usar modo manual.", Toast.LENGTH_LONG).show();
        }
    }


    private void alternarModoManual() {
        if (modoManual) {
            activarModoOCR();
        } else {
            activarModoManual();
        }
    }

    private void activarModoManual() {
        modoManual = true;
        bufferOCR.setLength(0);

        setCamposOcrEditables(true);

        txtEmpresa.setEnabled(true);
        txtPersonaVisitar.setEnabled(true);

        btnModoManual.setText("Volver a modo OCR");
        btnModoManual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#374151")));

        actualizarFechaHora();
        txtCedula.requestFocus();

        Toast.makeText(this, "Modo manual activo", Toast.LENGTH_SHORT).show();
    }

    private void activarModoOCR() {
        modoManual = false;
        bufferOCR.setLength(0);

        setCamposOcrEditables(false);

        txtEmpresa.setEnabled(true);
        txtPersonaVisitar.setEnabled(true);

        btnModoManual.setText("Modo manual");
        btnModoManual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#6B7280")));

        actualizarFechaHora();
        txtCedula.requestFocus();

        Toast.makeText(this, "Modo OCR activo", Toast.LENGTH_SHORT).show();
    }


    private void setCamposOcrEditables(boolean editable) {
        txtCedula.setEnabled(editable);
        txtFecha.setEnabled(editable);
        txtApellido1.setEnabled(editable);
        txtApellido2.setEnabled(editable);
        txtNombre.setEnabled(editable);
    }

    private void mostrarConfirmacionGuardar() {
        if (!validarDatos()) return;

        String mensaje =
                "Cédula: " + valor(txtCedula) + "\n" +
                        "Fecha nacimiento: " + valor(txtFecha) + "\n" +
                        "Apellido 1: " + valor(txtApellido1) + "\n" +
                        "Apellido 2: " + valor(txtApellido2) + "\n" +
                        "Nombre: " + valor(txtNombre) + "\n" +
                        "Empresa: " + valor(txtEmpresa) + "\n" +
                        "Persona a visitar: " + valor(txtPersonaVisitar) + "\n" +
                        "Fecha lectura: " + valor(txtFechaRegistro) + "\n" +
                        "Hora lectura: " + valor(txtHoraRegistro);

        new AlertDialog.Builder(this)
                .setTitle("Confirmar datos")
                .setMessage(mensaje)
                .setPositiveButton("Confirmar", (dialog, which) -> guardarCSV())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private boolean validarDatos() {
        if (valor(txtCedula).isEmpty()) {
            Toast.makeText(this, "Ingrese cédula", Toast.LENGTH_SHORT).show();
            txtCedula.requestFocus();
            return false;
        }

        if (!fechaNacimientoValida(valor(txtFecha))) {
            Toast.makeText(this, "Fecha nacimiento inválida. Use dd/MM/yyyy", Toast.LENGTH_LONG).show();
            txtFecha.requestFocus();
            return false;
        }

        if (valor(txtNombre).isEmpty()) {
            Toast.makeText(this, "Ingrese nombre", Toast.LENGTH_SHORT).show();
            txtNombre.requestFocus();
            return false;
        }

        if (valor(txtEmpresa).isEmpty()) {
            Toast.makeText(this, "Ingrese empresa", Toast.LENGTH_SHORT).show();
            txtEmpresa.requestFocus();
            return false;
        }

        if (valor(txtPersonaVisitar).isEmpty()) {
            Toast.makeText(this, "Ingrese persona a visitar", Toast.LENGTH_SHORT).show();
            txtPersonaVisitar.requestFocus();
            return false;
        }

        return true;
    }

    private boolean fechaNacimientoValida(String fecha) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            sdf.setLenient(false);

            Date fechaNac = sdf.parse(fecha);
            Date hoy = new Date();

            Calendar cal = Calendar.getInstance();
            int anioActual = cal.get(Calendar.YEAR);

            Calendar calNac = Calendar.getInstance();
            calNac.setTime(fechaNac);
            int anioNac = calNac.get(Calendar.YEAR);

            if (anioNac < 1900) return false;
            if (anioNac > anioActual) return false;
            if (fechaNac.after(hoy)) return false;

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private void guardarCSV() {
        try {
            File dir = new File(getExternalFilesDir(null), "export");
            if (!dir.exists()) dir.mkdirs();

            File archivo = new File(dir, "datos_ocr.csv");
            boolean nuevo = !archivo.exists() || archivo.length() == 0;

            FileWriter fw = new FileWriter(archivo, true);

            if (nuevo) {

                fw.write(CSV_HEADER);
            }

            fw.write(
                    limpiarCSV(valor(txtCedula)) + "," +
                            limpiarCSV(valor(txtFecha)) + "," +
                            limpiarCSV(valor(txtApellido1)) + "," +
                            limpiarCSV(valor(txtApellido2)) + "," +
                            limpiarCSV(valor(txtNombre)) + "," +
                            limpiarCSV(valor(txtEmpresa)) + "," +
                            limpiarCSV(valor(txtPersonaVisitar)) + "," +
                            limpiarCSV(valor(txtFechaRegistro)) + "," +
                            limpiarCSV(valor(txtHoraRegistro)) + "\n"
            );

            fw.close();

            guardarFirebase();

            bufferOCR.setLength(0);
            limpiarCamposSinMensaje();

            activarModoOCR();

            Toast.makeText(this, "Datos guardados", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void guardarFirebase() {
        Map<String, Object> visita = new HashMap<>();

        String nombreCompleto = valor(txtNombre);
        String apellidoCompleto = (valor(txtApellido1) + " " + valor(txtApellido2)).trim();
        String mesAnio = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());

        visita.put("visitanteNombre", nombreCompleto);
        visita.put("visitanteApellido", apellidoCompleto);
        visita.put("visitanteCedula", valor(txtCedula));
        visita.put("visitanteFechaNacimiento", valor(txtFecha));

        visita.put("empresa", valor(txtEmpresa));

        if (!empresaNombre.trim().isEmpty()) {
            visita.put("empresaSistemaNombre", empresaNombre);
        }
        visita.put("personaVisitableId", "");
        visita.put("personaVisitableNombre", valor(txtPersonaVisitar));

        visita.put("fechaLectura", valor(txtFechaRegistro));
        visita.put("horaLectura", valor(txtHoraRegistro));
        visita.put("fecha", new Date());
        visita.put("mesAnio", mesAnio);

        /*
         * MULTIEMPRESA
         *
         * empresaId identifica a la empresa dueña de estos datos.
         * El valor viene del perfil usuarios/{uid} autenticado.
         *
         * organizacionId se conserva temporalmente para compatibilidad
         * con registros/versiones anteriores, pero ya no es un valor fijo.
         */
        visita.put("empresaId", empresaId);
        visita.put("organizacionId", empresaId);
        visita.put("creadoPorUid", usuarioUid);
        visita.put("origen", "OCR Honeywell");
        visita.put("dispositivo", DISPOSITIVO);

        db.collection("visitas")
                .add(visita)
                .addOnSuccessListener(documentReference ->
                        Toast.makeText(this, "Visita guardada en Firebase", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error Firebase: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void mostrarMenuConfiguracion() {
        if (!"admin_empresa".equals(usuarioRol)) {
            Toast.makeText(
                    this,
                    "Esta opción está disponible únicamente para administradores.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Configuración")
                .setItems(new CharSequence[]{
                        "Ver visitas de hoy",
                        "Exportar / Guardar",
                        "Vaciar lecturas locales"
                }, (dialog, which) -> {
                    if (which == 0) {
                        consultarVisitasHoy();
                    } else if (which == 1) {
                        mostrarMenuExportacion();
                    } else {
                        confirmarVaciarLote();
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }


    private void mostrarMenuExportacion() {
        new AlertDialog.Builder(this)
                .setTitle("Exportar / Guardar")
                .setItems(
                        new CharSequence[]{
                                "Elegir ubicación XLS"
                        },
                        (dialog, which) ->
                                elegirUbicacionExportacionXls()
                )
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void restaurarLoteHoyDesdeFirebase() {
        Calendar inicio = Calendar.getInstance();
        inicio.set(Calendar.HOUR_OF_DAY, 0);
        inicio.set(Calendar.MINUTE, 0);
        inicio.set(Calendar.SECOND, 0);
        inicio.set(Calendar.MILLISECOND, 0);

        Calendar fin = Calendar.getInstance();
        fin.set(Calendar.HOUR_OF_DAY, 23);
        fin.set(Calendar.MINUTE, 59);
        fin.set(Calendar.SECOND, 59);
        fin.set(Calendar.MILLISECOND, 999);

        db.collection("visitas")
                .whereEqualTo("empresaId", empresaId)
                .whereGreaterThanOrEqualTo("fecha", inicio.getTime())
                .whereLessThanOrEqualTo("fecha", fin.getTime())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        File dir = new File(getExternalFilesDir(null), "export");
                        if (!dir.exists()) dir.mkdirs();

                        File archivo = new File(dir, "datos_ocr.csv");
                        FileWriter fw = new FileWriter(archivo, false);

                        fw.write(CSV_HEADER);

                        int contador = 0;

                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            fw.write(
                                    limpiarCSV(safe(doc.getString("visitanteCedula"))) + "," +
                                            limpiarCSV(safe(doc.getString("visitanteFechaNacimiento"))) + "," +
                                            limpiarCSV(obtenerApellido1(safe(doc.getString("visitanteApellido")))) + "," +
                                            limpiarCSV(obtenerApellido2(safe(doc.getString("visitanteApellido")))) + "," +
                                            limpiarCSV(safe(doc.getString("visitanteNombre"))) + "," +
                                            limpiarCSV(safe(doc.getString("empresa"))) + "," +
                                            limpiarCSV(safe(doc.getString("personaVisitableNombre"))) + "," +
                                            limpiarCSV(safe(doc.getString("fechaLectura"))) + "," +
                                            limpiarCSV(safe(doc.getString("horaLectura"))) + "\n"
                            );
                            contador++;
                        }

                        fw.close();

                        if (contador == 0) {
                            Toast.makeText(this, "No hay visitas de hoy en Firebase", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Lote restaurado: " + contador + " registros", Toast.LENGTH_LONG).show();
                        }

                    } catch (Exception e) {
                        Toast.makeText(this, "Error restaurando lote: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error Firebase: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private String obtenerApellido1(String apellidoCompleto) {
        String[] partes = apellidoCompleto.trim().split(" ");
        return partes.length > 0 ? partes[0] : "";
    }

    private String obtenerApellido2(String apellidoCompleto) {
        String[] partes = apellidoCompleto.trim().split(" ");
        return partes.length > 1 ? partes[1] : "";
    }


    private void consultarVisitasHoy() {
        Calendar inicio = Calendar.getInstance();
        inicio.set(Calendar.HOUR_OF_DAY, 0);
        inicio.set(Calendar.MINUTE, 0);
        inicio.set(Calendar.SECOND, 0);
        inicio.set(Calendar.MILLISECOND, 0);

        Calendar fin = Calendar.getInstance();
        fin.set(Calendar.HOUR_OF_DAY, 23);
        fin.set(Calendar.MINUTE, 59);
        fin.set(Calendar.SECOND, 59);
        fin.set(Calendar.MILLISECOND, 999);

        db.collection("visitas")
                .whereEqualTo("empresaId", empresaId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    LinearLayout contenedor = new LinearLayout(this);
                    contenedor.setOrientation(LinearLayout.VERTICAL);
                    contenedor.setPadding(12, 12, 12, 12);

                    TextView titulo = new TextView(this);
                    titulo.setText("Registros del día");
                    titulo.setTextSize(18);
                    titulo.setTextColor(Color.BLACK);
                    titulo.setGravity(Gravity.CENTER);
                    titulo.setPadding(0, 0, 0, 16);
                    contenedor.addView(titulo);

                    TableLayout tabla = new TableLayout(this);
                    tabla.setStretchAllColumns(false);
                    tabla.setShrinkAllColumns(false);

                    TableRow encabezado = new TableRow(this);
                    encabezado.setBackgroundColor(
                            Color.parseColor("#E5E7EB")
                    );

                    encabezado.addView(crearCelda("Hora", true));
                    encabezado.addView(crearCelda("Cédula", true));
                    encabezado.addView(crearCelda("Nombre", true));
                    encabezado.addView(crearCelda("Apellido", true));
                    encabezado.addView(crearCelda("Empresa", true));
                    encabezado.addView(crearCelda("Visita a", true));

                    tabla.addView(encabezado);

                    int contador = 0;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {

                        Object fechaObjeto = doc.get("fecha");

                        Date fechaVisita = null;

                        if (fechaObjeto instanceof com.google.firebase.Timestamp) {
                            fechaVisita =
                                    ((com.google.firebase.Timestamp) fechaObjeto)
                                            .toDate();
                        } else if (fechaObjeto instanceof Date) {
                            fechaVisita = (Date) fechaObjeto;
                        }

                        if (fechaVisita == null) {
                            continue;
                        }

                        long tiempo = fechaVisita.getTime();

                        if (
                                tiempo < inicio.getTimeInMillis() ||
                                        tiempo > fin.getTimeInMillis()
                        ) {
                            continue;
                        }

                        String hora =
                                safe(doc.getString("horaLectura"));

                        String cedula =
                                safe(doc.getString("visitanteCedula"));

                        String nombre =
                                safe(doc.getString("visitanteNombre"));

                        String apellido =
                                safe(doc.getString("visitanteApellido"));

                        String empresa =
                                safe(doc.getString("empresa"));

                        String persona =
                                safe(doc.getString("personaVisitableNombre"));

                        TableRow fila = new TableRow(this);

                        fila.addView(crearCelda(hora, false));
                        fila.addView(crearCelda(cedula, false));
                        fila.addView(crearCelda(nombre, false));
                        fila.addView(crearCelda(apellido, false));
                        fila.addView(crearCelda(empresa, false));
                        fila.addView(crearCelda(persona, false));

                        tabla.addView(fila);

                        contador++;
                    }

                    if (contador == 0) {

                        TextView sinDatos = new TextView(this);

                        sinDatos.setText(
                                "Sin visitas registradas para el día de hoy."
                        );

                        sinDatos.setTextSize(16);
                        sinDatos.setTextColor(Color.DKGRAY);
                        sinDatos.setGravity(Gravity.CENTER);
                        sinDatos.setPadding(20, 40, 20, 40);

                        contenedor.addView(sinDatos);

                    } else {

                        HorizontalScrollView scrollHorizontal =
                                new HorizontalScrollView(this);

                        scrollHorizontal.addView(tabla);

                        contenedor.addView(scrollHorizontal);
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Visitas de hoy")
                            .setView(contenedor)
                            .setPositiveButton("Aceptar", null)
                            .show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "No fue posible consultar las visitas. Intente nuevamente.",
                                Toast.LENGTH_LONG
                        ).show()
                );
    }


    private TextView crearCelda(String texto, boolean encabezado) {
        TextView celda = new TextView(this);


        celda.setText(texto);
        celda.setTextSize(encabezado ? 14 : 13);
        celda.setTextColor(Color.BLACK);
        celda.setPadding(16, 12, 16, 12);
        celda.setMinWidth(160);
        celda.setGravity(Gravity.CENTER_VERTICAL);

        if (encabezado) {
            celda.setTypeface(null, android.graphics.Typeface.BOLD);
            celda.setBackgroundColor(Color.parseColor("#E5E7EB"));
        } else {
            celda.setBackgroundColor(Color.WHITE);
        }

        return celda;
    }

    private void exportarDatosDownloads() {
        try {
            File origen = new File(getExternalFilesDir(null), "export/datos_ocr.csv");

            if (!origen.exists() || !csvTieneDatos(origen)) {
                Toast.makeText(this, "No hay datos guardados para exportar", Toast.LENGTH_LONG).show();
                return;
            }

            String fechaHora = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String nombreArchivo = "datos_ocr_" + fechaHora + ".csv";

            File carpetaDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!carpetaDownloads.exists()) carpetaDownloads.mkdirs();

            File destino = new File(carpetaDownloads, nombreArchivo);

            copiarArchivo(origen, destino);

            MediaScannerConnection.scanFile(
                    this,
                    new String[]{destino.getAbsolutePath()},
                    new String[]{"text/csv"},
                    null
            );

            Toast.makeText(this, "Copia creada en: " + destino.getAbsolutePath(), Toast.LENGTH_LONG).show();

        } catch (Throwable e) {
            Toast.makeText(this, "Error exportando: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void elegirUbicacionExportacion() {
        String fechaHora = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String nombreArchivo = "datos_ocr_" + fechaHora + ".csv";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, nombreArchivo);
        startActivityForResult(intent, REQUEST_EXPORTAR_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();

            if (requestCode == REQUEST_EXPORTAR_CSV) {
                exportarAUri(uri);
            } else if (requestCode == REQUEST_EXPORTAR_XLS) {
                exportarExcelAUri(uri);
            }
        }
    }

    private void exportarAUri(Uri uri) {
        try {
            File origen = new File(getExternalFilesDir(null), "export/datos_ocr.csv");

            if (!origen.exists() || !csvTieneDatos(origen)) {
                Toast.makeText(this, "No hay datos guardados para exportar", Toast.LENGTH_LONG).show();
                return;
            }

            OutputStream salida = getContentResolver().openOutputStream(uri);
            FileInputStream entrada = new FileInputStream(origen);

            byte[] buffer = new byte[4096];
            int leidos;

            while ((leidos = entrada.read(buffer)) != -1) {
                salida.write(buffer, 0, leidos);
            }

            entrada.close();
            salida.close();

            Toast.makeText(this, "CSV exportado correctamente", Toast.LENGTH_LONG).show();

        } catch (Throwable e) {
            Toast.makeText(this, "Error al exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copiarArchivo(File origen, File destino) throws Exception {
        FileInputStream entrada = new FileInputStream(origen);
        FileOutputStream salida = new FileOutputStream(destino);

        byte[] buffer = new byte[4096];
        int leidos;

        while ((leidos = entrada.read(buffer)) != -1) {
            salida.write(buffer, 0, leidos);
        }

        entrada.close();
        salida.close();
    }

    private void limpiarCampos() {
        bufferOCR.setLength(0);
        limpiarCamposSinMensaje();


        activarModoOCR();


        txtCedula.requestFocus();
        Toast.makeText(this, "Campos limpios", Toast.LENGTH_SHORT).show();
    }

    private void limpiarCamposSinMensaje() {
        txtCedula.setText("");
        txtFecha.setText("");
        txtApellido1.setText("");
        txtApellido2.setText("");
        txtNombre.setText("");
        txtEmpresa.setText("");
        txtPersonaVisitar.setText("");
        actualizarFechaHora();
        txtCedula.requestFocus();
    }

    private void actualizarFechaHora() {
        Date ahora = new Date();
        txtFechaRegistro.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(ahora));
        txtHoraRegistro.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(ahora));
    }

    private String formatearFechaNacimiento(String valor) {
        valor = valor.replace("/", "").replace("-", "").trim();

        if (valor.length() == 6) {
            String yy = valor.substring(0, 2);
            String mm = valor.substring(2, 4);
            String dd = valor.substring(4, 6);

            int anioCorto = Integer.parseInt(yy);
            int anioActualCorto = Calendar.getInstance().get(Calendar.YEAR) % 100;

            int anioCompleto;
            if (anioCorto > anioActualCorto) {
                anioCompleto = 1900 + anioCorto;
            } else {
                anioCompleto = 2000 + anioCorto;
            }

            return dd + "/" + mm + "/" + anioCompleto;
        }

        if (valor.length() == 8) {
            String yyyy = valor.substring(0, 4);
            String mm = valor.substring(4, 6);
            String dd = valor.substring(6, 8);
            return dd + "/" + mm + "/" + yyyy;
        }

        return valor;
    }

    private String capitalizar(String texto) {
        texto = texto.toLowerCase(Locale.ROOT).trim();
        if (texto.isEmpty()) return "";
        return texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
    }

    private String limpiarCSV(String valor) {
        return valor.replace(",", " ").replace("\n", " ").trim();
    }

    private void vaciarCSVInterno() {
        try {
            File dir = new File(getExternalFilesDir(null), "export");
            if (!dir.exists()) dir.mkdirs();

            File archivo = new File(dir, "datos_ocr.csv");

            FileWriter fw = new FileWriter(archivo, false);
            fw.write(CSV_HEADER);
            fw.close();

        } catch (Exception e) {
            Toast.makeText(this, "Error vaciando lote: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean csvTieneDatos(File archivo) {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(archivo));
            int lineas = 0;

            while (br.readLine() != null) {
                lineas++;
                if (lineas > 1) {
                    br.close();
                    return true;
                }
            }

            br.close();
        } catch (Exception ignored) {
        }

        return false;
    }

    private void confirmarVaciarLote() {
        new AlertDialog.Builder(this)
                .setTitle("Vaciar lote interno")
                .setMessage("Esto borra las lecturas guardadas dentro de la app. Use esta opción solo después de confirmar que el CSV fue copiado correctamente.")
                .setPositiveButton("Vaciar", (dialog, which) -> {
                    vaciarCSVInterno();
                    Toast.makeText(this, "Lote interno vacío", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String valor(EditText editText) {
        return editText.getText().toString().trim();
    }

    private String safe(String valor) {
        return valor != null ? valor : "";
    }


    private void configurarLimitesCampos() {
        txtCedula.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(10)
        });

        txtFecha.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(10)
        });

        txtApellido1.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(50),
                soloLetrasFiltro()
        });

        txtApellido2.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(50),
                soloLetrasFiltro()
        });

        txtNombre.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(50),
                soloLetrasFiltro()
        });

        txtEmpresa.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(30)
        });

        txtPersonaVisitar.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(50),
                soloLetrasFiltro()
        });
    }

    private void configurarMascaraFecha() {
        txtFecha.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (editandoFecha) return;

                editandoFecha = true;

                String limpio = s.toString().replaceAll("[^0-9]", "");

                if (limpio.length() > 8) {
                    limpio = limpio.substring(0, 8);
                }

                // Día
                if (limpio.length() >= 2) {
                    int dia = Integer.parseInt(limpio.substring(0, 2));
                    if (dia < 1 || dia > 31) {
                        limpio = limpio.substring(0, 1);
                        Toast.makeText(MainActivity.this, "Día inválido", Toast.LENGTH_SHORT).show();
                    }
                }

                // Mes
                if (limpio.length() >= 4) {
                    int mes = Integer.parseInt(limpio.substring(2, 4));
                    if (mes < 1 || mes > 12) {
                        limpio = limpio.substring(0, 3);
                        Toast.makeText(MainActivity.this, "Mes inválido", Toast.LENGTH_SHORT).show();
                    }
                }

                // Año completo
                if (limpio.length() == 8) {
                    int anio = Integer.parseInt(limpio.substring(4, 8));
                    int anioActual = Calendar.getInstance().get(Calendar.YEAR);

                    if (anio < 1900 || anio > anioActual) {
                        limpio = limpio.substring(0, 4);
                        Toast.makeText(MainActivity.this, "Año inválido", Toast.LENGTH_SHORT).show();
                    }
                }

                StringBuilder fecha = new StringBuilder();

                for (int i = 0; i < limpio.length(); i++) {
                    if (i == 2 || i == 4) {
                        fecha.append("/");
                    }
                    fecha.append(limpio.charAt(i));
                }

                txtFecha.setText(fecha.toString());
                txtFecha.setSelection(txtFecha.getText().length());

                editandoFecha = false;
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        relojHandler.removeCallbacks(relojRunnable);
    }


    private InputFilter soloLetrasFiltro() {
        return (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);

                if (!Character.isLetter(c) && c != ' ' && c != 'ñ' && c != 'Ñ') {
                    return "";
                }
            }
            return null;
        };
    }

    private void exportarExcelDownloads() {
        try {
            File origen = new File(getExternalFilesDir(null), "export/datos_ocr.csv");

            if (!origen.exists() || !csvTieneDatos(origen)) {
                Toast.makeText(this, "No hay datos guardados para exportar", Toast.LENGTH_LONG).show();
                return;
            }

            String fechaHora = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String nombreArchivo = "datos_ocr_" + fechaHora + ".xls";

            File carpetaDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!carpetaDownloads.exists()) carpetaDownloads.mkdirs();

            File destino = new File(carpetaDownloads, nombreArchivo);

            String htmlExcel = convertirCsvAExcelHtml(origen);

            FileWriter fw = new FileWriter(destino, false);
            fw.write(htmlExcel);
            fw.close();

            MediaScannerConnection.scanFile(
                    this,
                    new String[]{destino.getAbsolutePath()},
                    new String[]{"application/vnd.ms-excel"},
                    null
            );

            Toast.makeText(this, "Excel creado en: " + destino.getAbsolutePath(), Toast.LENGTH_LONG).show();

        } catch (Throwable e) {
            Toast.makeText(this, "Error exportando Excel: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }



    private String convertirCsvAExcelHtml(File archivoCsv) throws Exception {
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(archivoCsv));

        StringBuilder html = new StringBuilder();

        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("</head>");
        html.append("<body>");
        html.append("<table border='1'>");

        String linea;
        boolean encabezado = true;

        while ((linea = br.readLine()) != null) {
            String[] columnas = linea.split(",", -1);

            html.append("<tr>");

            for (String columna : columnas) {
                String valor = escaparHtml(columna);

                if (encabezado) {
                    html.append("<th style='background-color:#D9EAF7;font-weight:bold;'>")
                            .append(valor)
                            .append("</th>");
                } else {
                    html.append("<td>")
                            .append(valor)
                            .append("</td>");
                }
            }

            html.append("</tr>");
            encabezado = false;
        }

        html.append("</table>");
        html.append("</body>");
        html.append("</html>");

        br.close();

        return html.toString();
    }

    private String escaparHtml(String texto) {
        if (texto == null) return "";

        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }


    private void elegirUbicacionExportacionXls() {
        String fechaHora = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String nombreArchivo = "datos_ocr_" + fechaHora + ".xls";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.ms-excel");
        intent.putExtra(Intent.EXTRA_TITLE, nombreArchivo);
        startActivityForResult(intent, REQUEST_EXPORTAR_XLS);
    }


    private void exportarExcelAUri(Uri uri) {
        try {
            File origen = new File(getExternalFilesDir(null), "export/datos_ocr.csv");

            if (origen.exists() && csvTieneDatos(origen)) {
                String htmlExcel = convertirCsvAExcelHtml(origen);

                OutputStream salida = getContentResolver().openOutputStream(uri);
                salida.write(htmlExcel.getBytes("UTF-8"));
                salida.close();

                Toast.makeText(this, "Excel exportado correctamente", Toast.LENGTH_LONG).show();
            } else {
                exportarExcelDesdeFirebaseHoy(uri);
            }

        } catch (Throwable e) {
            Toast.makeText(this, "Error al exportar Excel: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportarExcelDesdeFirebaseHoy(Uri uri) {
        Calendar inicio = Calendar.getInstance();
        inicio.set(Calendar.HOUR_OF_DAY, 0);
        inicio.set(Calendar.MINUTE, 0);
        inicio.set(Calendar.SECOND, 0);
        inicio.set(Calendar.MILLISECOND, 0);

        Calendar fin = Calendar.getInstance();
        fin.set(Calendar.HOUR_OF_DAY, 23);
        fin.set(Calendar.MINUTE, 59);
        fin.set(Calendar.SECOND, 59);
        fin.set(Calendar.MILLISECOND, 999);

        db.collection("visitas")
                .whereEqualTo("empresaId", empresaId)
                .whereGreaterThanOrEqualTo("fecha", inicio.getTime())
                .whereLessThanOrEqualTo("fecha", fin.getTime())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        if (queryDocumentSnapshots.isEmpty()) {
                            Toast.makeText(this, "No hay visitas de hoy en Firebase", Toast.LENGTH_LONG).show();
                            return;
                        }

                        File archivoLocal = reconstruirCsvLocalDesdeFirebase(queryDocumentSnapshots);
                        String htmlExcel = convertirCsvAExcelHtml(archivoLocal);

                        OutputStream salida = getContentResolver().openOutputStream(uri);
                        salida.write(htmlExcel.getBytes("UTF-8"));
                        salida.close();

                        Toast.makeText(this, "Excel creado desde Firebase y lote local restaurado", Toast.LENGTH_LONG).show();

                    } catch (Exception e) {
                        Toast.makeText(this, "Error exportando desde Firebase: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error Firebase: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }


    private File reconstruirCsvLocalDesdeFirebase(QuerySnapshot queryDocumentSnapshots) throws Exception {
        File dir = new File(getExternalFilesDir(null), "export");
        if (!dir.exists()) dir.mkdirs();

        File archivo = new File(dir, "datos_ocr.csv");
        FileWriter fw = new FileWriter(archivo, false);

        fw.write(CSV_HEADER);

        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
            fw.write(
                    limpiarCSV(safe(doc.getString("visitanteCedula"))) + "," +
                            limpiarCSV(safe(doc.getString("visitanteFechaNacimiento"))) + "," +
                            limpiarCSV(obtenerApellido1(safe(doc.getString("visitanteApellido")))) + "," +
                            limpiarCSV(obtenerApellido2(safe(doc.getString("visitanteApellido")))) + "," +
                            limpiarCSV(safe(doc.getString("visitanteNombre"))) + "," +
                            limpiarCSV(safe(doc.getString("empresa"))) + "," +
                            limpiarCSV(safe(doc.getString("personaVisitableNombre"))) + "," +
                            limpiarCSV(safe(doc.getString("fechaLectura"))) + "," +
                            limpiarCSV(safe(doc.getString("horaLectura"))) + "\n"
            );
        }

        fw.close();
        return archivo;
    }

}
