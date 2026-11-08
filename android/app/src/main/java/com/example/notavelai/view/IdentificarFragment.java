package com.example.notavelai.view;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.notavelai.R;
import com.example.notavelai.databinding.FragmentIdentificarBinding;
import com.example.notavelai.util.Formato;
import com.example.notavelai.util.Preferencias;
import com.example.notavelai.viewmodel.IdentificarViewModel;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Fragmento 1 - Identificar: a função principal do app.
 *
 * Fotografa (câmera externa) ou escolhe da galeria uma foto da cédula e mostra e fala o valor
 * em voz alta (TextToSpeech). A análise, o modelo e o último resultado ficam no
 * IdentificarViewModel, que sobrevive quando a tela é recriada (por exemplo, ao voltar da câmera
 * com o celular deitado); este fragmento só observa o estado e o mostra.
 *
 * Threads: o modelo, a leitura da foto e o banco rodam numa Thread do ViewModel, e o resultado
 * volta para a interface pelo LiveData, na thread principal.
 */
public class IdentificarFragment extends Fragment implements TextToSpeech.OnInitListener {

    private static final String ESTADO_ARQUIVO_CAMERA = "arquivo_camera";
    private static final String ORIGEM_CAMERA = "camera";
    private static final String ORIGEM_GALERIA = "galeria";

    private FragmentIdentificarBinding binding;
    private IdentificarViewModel viewModel;

    private TextToSpeech fala;
    private boolean falaPronta = false;
    /** Frase que chegou antes da voz ficar pronta; é falada no onInit. */
    private String falaPendente;
    /** Última frase mostrada, para o botão "Ouvir de novo". */
    private String ultimaFrase;

    /** Arquivo onde a câmera externa grava a foto. É apagado depois da análise. */
    private File photoFile;

    // Launcher para solicitação de permissão da câmera
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    new ActivityResultCallback<Boolean>() {
                        @Override
                        public void onActivityResult(Boolean concedida) {
                            if (concedida) {
                                abrirCamera();
                            } else {
                                avisar(getString(R.string.msg_permissao_camera));
                            }
                        }
                    });

    // Launcher que dispara a câmera externa e recebe o resultado
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(),
                    new ActivityResultCallback<Boolean>() {
                        @Override
                        public void onActivityResult(Boolean sucesso) {
                            if (sucesso && photoFile != null) {
                                viewModel.analisar(Uri.fromFile(photoFile), photoFile, ORIGEM_CAMERA);
                            } else if (photoFile != null) {
                                photoFile.delete();
                            }
                            photoFile = null;
                        }
                    });

    // Launcher da galeria: o sistema entrega só a imagem escolhida, sem pedir permissão
    private final ActivityResultLauncher<String> galeriaLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    new ActivityResultCallback<Uri>() {
                        @Override
                        public void onActivityResult(Uri uri) {
                            if (uri != null) {
                                viewModel.analisar(uri, null, ORIGEM_GALERIA);
                            }
                        }
                    });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            String caminho = savedInstanceState.getString(ESTADO_ARQUIVO_CAMERA);
            if (caminho != null) {
                photoFile = new File(caminho);
            }
        }
        viewModel = new ViewModelProvider(this).get(IdentificarViewModel.class);
        fala = new TextToSpeech(requireContext().getApplicationContext(), this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentIdentificarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonFotografar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checarPermissaoECamera();
            }
        });
        binding.buttonGaleria.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                galeriaLauncher.launch("image/*");
            }
        });
        binding.buttonOuvirNovamente.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                falar(ultimaFrase, true);
            }
        });
        binding.buttonOuvirNovamente.setEnabled(false);

        // Mostra o estado atual (inclusive um resultado que chegou enquanto a tela era recriada).
        viewModel.getEstado().observe(getViewLifecycleOwner(), new Observer<IdentificarViewModel.Estado>() {
            @Override
            public void onChanged(IdentificarViewModel.Estado estado) {
                mostrar(estado);
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (photoFile != null) {
            outState.putString(ESTADO_ARQUIVO_CAMERA, photoFile.getAbsolutePath());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fala.stop();
        fala.shutdown();
    }

    // Tela

    private void mostrar(IdentificarViewModel.Estado estado) {
        if (binding == null || estado == null) {
            return;
        }
        binding.buttonFotografar.setEnabled(!estado.analisando);
        binding.buttonGaleria.setEnabled(!estado.analisando);
        binding.progressAnalise.setVisibility(estado.analisando ? View.VISIBLE : View.GONE);

        if (estado.foto != null) {
            binding.imageCedula.setImageBitmap(estado.foto);
            binding.imageCedula.setVisibility(View.VISIBLE);
        }
        binding.textValor.setText(estado.valor);
        binding.textConfianca.setText(estado.confianca);
        // O texto do resultado é uma "live region": o TalkBack lê a mudança sozinho.
        binding.textResultado.setText(estado.frase);

        if (!estado.analisando) {
            ultimaFrase = estado.frase;
            binding.buttonOuvirNovamente.setEnabled(true);
            // Cada resultado é falado uma vez só, mesmo que a tela seja recriada depois.
            if (viewModel.marcarComoFalado(estado)) {
                falar(estado.frase, false);
            }
        }
    }

    // Voz

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int resultado = fala.setLanguage(Formato.BRASIL);
            falaPronta = resultado != TextToSpeech.LANG_MISSING_DATA
                    && resultado != TextToSpeech.LANG_NOT_SUPPORTED;
        }
        if (falaPronta && falaPendente != null) {
            fala.speak(falaPendente, TextToSpeech.QUEUE_FLUSH, null, "resultado");
        }
        falaPendente = null;
    }

    /**
     * Fala a frase com a voz do aparelho, se a opção estiver ligada. "forcar" ignora a
     * opção: é usado pelo botão "Ouvir de novo", que o usuário toca justamente para ouvir.
     * Se a voz ainda estiver iniciando, a frase espera e é falada no onInit.
     */
    private void falar(String frase, boolean forcar) {
        if (frase == null || getContext() == null) {
            return;
        }
        if (!forcar && !Preferencias.getFalarResultado(requireContext())) {
            return;
        }
        if (falaPronta) {
            fala.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "resultado");
        } else {
            falaPendente = frase;
        }
    }

    // Câmera

    private void checarPermissaoECamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            abrirCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private File criarArquivoImagem() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File pasta = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("CEDULA_" + timeStamp + "_", ".jpg", pasta);
    }

    private void abrirCamera() {
        try {
            photoFile = criarArquivoImagem();
            Uri photoUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", photoFile);
            takePictureLauncher.launch(photoUri);
        } catch (IOException e) {
            avisar(getString(R.string.msg_erro_imagem));
        } catch (ActivityNotFoundException e) {
            avisar(getString(R.string.msg_sem_camera));
        }
    }

    /** Mensagem curta na tela, também falada (quem não enxerga não lê o Toast). */
    private void avisar(String mensagem) {
        Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
        if (binding != null) {
            binding.textResultado.setText(mensagem);
        }
        falar(mensagem, false);
    }
}
