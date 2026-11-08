# NotavelAI · app Android

App do [NotavelAI](../README.md): fotografa uma cédula de Real, identifica o valor com o modelo de
[`ml/`](../ml/README.md) e fala o valor em voz alta. Projeto do Android Studio em Java (template
Basic Views Activity, minSdk 26, targetSdk 37).

## Modelo

O modelo (`notavelai.tflite`, 8,9 MB) não fica no Git. **Não é preciso baixá-lo à mão:** na primeira
compilação, a tarefa `baixarModelo` do Gradle o baixa do
[Hugging Face](https://huggingface.co/arthursilvadantas/NotavelAI) para `app/src/main/assets/`, numa
versão fixa, e confere o SHA-256. Depois disso ela é pulada. A revisão e o hash ficam no início de
`app/build.gradle.kts`.

## Compilar e testar

Abra esta pasta (`android/`) no Android Studio, ou use a linha de comando:

```bash
./gradlew assembleDebug          # APK em app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # pré-processamento Java x TensorFlow
./gradlew connectedDebugAndroidTest  # modelo x Python e acessibilidade (precisa de emulador)
```

O `ClassificadorCedulasTest` usa as fotos e a referência geradas por `export_android`, que não são
versionadas (são fotos pessoais); sem elas, rode só os outros testes.

| Teste | O que confere |
| --- | --- |
| `PreProcessamentoTest` (JVM) | corte central em Java igual ao `smart_resize` do Keras usado no treino, com imagens de referência geradas pelo Keras |
| `ClassificadorCedulasTest` (aparelho) | o `.tflite` roda no LiteRT e dá as mesmas respostas do Python nas fotos de `ml/data/phone_test/` |
| `AcessibilidadeTest` (aparelho) | Accessibility Test Framework nas telas de Login, Cadastro e Principal: rótulos, alvos de toque e contraste |

## Telas

| Tela | O que faz |
| --- | --- |
| Splash (`SplashActivity`) | Splash Screen API; consulta a sessão e vai para o Login ou a tela principal |
| Login (`LoginActivity`) | e-mail e senha, conferidos com BCrypt |
| Cadastro (`CadastroActivity`) | cadastro e edição de perfil, com foto da câmera ou da galeria |
| Identificar (`IdentificarFragment` + `IdentificarViewModel`) | fotografa a cédula, roda o modelo, fala o valor e salva no histórico |
| Histórico (`HistoricoFragment`) | ListView com BaseAdapter, total, plurais e menu de contexto para excluir |
| Cotação (`CotacaoFragment`) | consulta a AwesomeAPI com HttpURLConnection e converte o total |

## Decisões principais

- **Threads:** o Room é criado sem `allowMainThreadQueries()`. Banco, BCrypt, leitura de fotos,
  modelo e HTTP rodam em `new Thread(...)`, e a tela é atualizada com `runOnUiThread`, `Handler` ou
  `LiveData`.
- **Rotação:** a identificação fica no `IdentificarViewModel`, que sobrevive quando o Android recria
  a tela (por exemplo, ao voltar da câmera com o celular deitado); o resultado não se perde e é
  falado uma vez só.
- **Segurança:** senhas só como hash BCrypt (salt embutido; nova senha gera novo hash), senha atual
  exigida para editar o perfil, foto de perfil na pasta interna do app (o banco guarda o caminho) e
  backup desativado, porque o banco tem e-mails e hashes.
- **Acessibilidade:** paleta com contraste mínimo de 6:1 nos dois temas, rótulos visíveis ligados
  aos campos por `labelFor`, alvos de toque de 56dp ou mais, resultados em live regions para o
  TalkBack, valor falado pelo TextToSpeech e configurações de tema, tamanho do texto e fala.
- **Reconhecimento:** `PreProcessamento.java` pega o quadrado central da foto e o reduz a 224×224
  exatamente como o treino. Abaixo de 70% de confiança, o app diz que não conseguiu identificar em
  vez de arriscar um valor: nas 24 fotos de celular do grupo, fala 18 valores certos e nenhum
  errado; em 135 fotos reais do dataset Cédulas do Real, 127 certos e 2 errados.
- **Economia de energia:** a cotação só é consultada quando o usuário pede, uma consulta de menos de
  5 minutos é reaproveitada, e a conexão é verificada antes da requisição.
