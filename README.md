<div align="center">

<img src="docs/images/logo.svg" alt="Logo do NotavelAI: uma cédula com ondas sonoras" width="120">

# NotavelAI

**Aponte a câmera para uma cédula de Real e ouça o valor em voz alta.**<br>
Reconhecimento de cédulas no próprio celular, sem internet, para pessoas cegas ou com baixa visão.

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](android/)
[![Java](https://img.shields.io/badge/Java-11-ED8B00?logo=openjdk&logoColor=white)](android/)
[![TensorFlow Lite](https://img.shields.io/badge/LiteRT-TensorFlow%20Lite-FF6F00?logo=tensorflow&logoColor=white)](ml/)
[![Python](https://img.shields.io/badge/Python-3.12-3776AB?logo=python&logoColor=white)](ml/)
[![Hugging Face](https://img.shields.io/badge/%F0%9F%A4%97%20Modelo-Hugging%20Face-FFD21E)](https://huggingface.co/arthursilvadantas/NotavelAI)
[![Acurácia](https://img.shields.io/badge/acur%C3%A1cia-95%2C6%25-0B5D3B)](#resultados)
[![Acessibilidade](https://img.shields.io/badge/acessibilidade-TalkBack%20%C2%B7%20contraste%206%3A1-0B5D3B)](#decisões-de-arquitetura)

[**App Android**](android/README.md) · [**Modelo de ML**](ml/README.md) · [**Modelo no Hugging Face**](https://huggingface.co/arthursilvadantas/NotavelAI)

</div>

---

## Destaques

| 🔊 Fala o valor | 📴 Funciona offline | 🎯 95,6% de acerto | ♿ Acessível por padrão |
| :---: | :---: | :---: | :---: |
| O valor é anunciado pelo TextToSpeech e lido pelo TalkBack | O modelo (8,9 MB) roda no celular com LiteRT; só a cotação usa internet | Em fotos reais que o modelo nunca viu, com limiar de 70% para não arriscar | Contraste mínimo de 6:1, alvos de 56 dp e testes automáticos de acessibilidade |

O app também tem conta de usuário com senha protegida por BCrypt, histórico das cédulas
identificadas, total em reais convertido para outras moedas e notificações.

> **Aviso:** é um protótipo acadêmico. Ele ainda erra e não deve ser a única forma de conferir dinheiro.

## Sumário

- [Como funciona](#como-funciona)
- [Resultados](#resultados)
- [Arquitetura do app](#arquitetura-do-app)
- [Decisões de arquitetura](#decisões-de-arquitetura)
- [Tecnologias](#tecnologias)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Como rodar](#como-rodar)
- [Créditos e licenças](#créditos-e-licenças)

## Como funciona

O projeto tem dois ciclos: o **ciclo de ML**, no computador, que treina e publica o modelo, e o
**ciclo do app**, no celular, que usa o modelo.

![Arquitetura geral: ciclo de ML e ciclo do app](docs/images/arquitetura.png)

As duas partes se comunicam por um contrato pequeno, testado dos dois lados:

| | Contrato entre o modelo e o app |
| --- | --- |
| **Arquivos** | `notavelai.tflite` e `labels.txt` |
| **Entrada** | `[1, 224, 224, 3]`, RGB, float32, 0–255 sem normalizar: o quadrado central da foto, reduzido a 224×224 |
| **Saída** | 8 probabilidades: `2, 5, 10, 20, 50, 100, 200, fundo` |
| **Decisão** | anuncia o valor só com confiança de pelo menos 70% e classe diferente de `fundo` |
| **Garantia** | `PreProcessamentoTest` (Java igual ao Keras) e `ClassificadorCedulasTest` (celular igual ao Python) |

## Resultados

Medidos em fotos que o modelo **nunca viu no treino**. "Falados certos / errados" conta as vezes em
que o app anunciaria um valor.

| Conjunto de teste | Acertos | Falados certos / errados |
| --- | :---: | :---: |
| Cédulas do Real, metade do teste dos autores (135 fotos) | **95,6%** | 127 / 2 |
| Cédulas do Real, conjunto extra dos autores (66 fotos) | **93,9%** | 59 / 3 |
| Fotos de celular do grupo (24 fotos) | **79,2%** | 18 / 0 |

O modelo é uma **MobileNetV2** com ajuste fino da rede inteira, treinada com 2.713 imagens, entre
elas 1.142 fotos reais do dataset [Cédulas do Real](https://www.kaggle.com/datasets/karenalmeida340/cdulas-do-real).
Dados, treino, erros mais frequentes e limitações estão em [`ml/README.md`](ml/README.md) e no
[Hugging Face](https://huggingface.co/arthursilvadantas/NotavelAI).

## Arquitetura do app

Android nativo em Java, organizado em camadas:

![Camadas do app Android](docs/images/camadas.png)

| Pacote | Responsabilidade |
| --- | --- |
| `view/` | telas: `SplashActivity`, `LoginActivity`, `CadastroActivity` e `MainActivity`, com os fragmentos `IdentificarFragment`, `HistoricoFragment` e `CotacaoFragment` |
| `viewmodel/` | `IdentificarViewModel`: análise, modelo e último resultado, que sobrevivem à rotação da tela |
| `repository/` | regras sobre o banco: login, sessão, cadastro e histórico do usuário |
| `database/` | Room: tabelas, consultas (DAOs) e a instância única do banco |
| `ml/` | pré-processamento da foto (quadrado central) e execução do modelo com LiteRT |
| `rede/` | consulta de cotações na AwesomeAPI |
| `adapter/` | adapter da lista do histórico |
| `util/` | hash de senha, leitura de fotos, notificações, preferências e formatação |

<details>
<summary><b>Navegação entre as telas</b></summary>
<br>

![Navegação entre as telas](docs/images/navegacao.png)

</details>

<details>
<summary><b>Banco de dados (Room)</b></summary>
<br>

![Tabelas do banco Room](docs/images/banco.png)

Apagar um usuário apaga em cascata a sessão e o histórico dele.

</details>

<details>
<summary><b>Fluxo de uma identificação, da câmera à voz</b></summary>
<br>

![Fluxo de uma identificação](docs/images/fluxo_identificacao.png)

</details>

## Decisões de arquitetura

| | Decisão |
| --- | --- |
| 🧵 **Threads** | O Room é criado sem `allowMainThreadQueries()`. Banco, BCrypt, leitura de fotos, modelo e HTTP rodam em `new Thread(...)`, e a tela é atualizada com `runOnUiThread`, `Handler` ou `LiveData`. A interface nunca trava. |
| 🔄 **Rotação** | A identificação fica no `IdentificarViewModel`. Ao voltar da câmera com o celular deitado, o Android recria a tela, e o resultado não se perde nem deixa de ser falado. |
| 📴 **Offline primeiro** | O reconhecimento não usa internet. Só a cotação depende de rede, com verificação de conexão antes e cache de 5 minutos. |
| 🔒 **Segurança** | Senhas guardadas só como hash BCrypt; senha atual exigida para editar o perfil; foto de perfil na pasta interna do app; backup do Android desativado. |
| ♿ **Acessibilidade** | Contraste mínimo de 6:1 nos temas claro e escuro, alvos de toque de 56 dp, rótulos para o TalkBack, resultados em live regions, valor falado e ajustes de tema, tamanho do texto e fala. Verificado pelo `AcessibilidadeTest`. |
| 🤐 **Resposta conservadora** | Abaixo de 70% de confiança, o app diz que não conseguiu identificar em vez de arriscar um valor errado. |
| 📦 **Reprodutível** | O modelo não fica no Git: o Gradle o baixa do Hugging Face na primeira compilação, numa versão fixa e conferida por SHA-256. |

## Tecnologias

| Área | Tecnologias |
| --- | --- |
| **App** | Java 11 · Android SDK 37 (mínimo 26) · AndroidX · Material Components · Navigation · ViewBinding · Splash Screen API |
| **Dados no app** | Room 2.6 (SQLite) · SharedPreferences |
| **Segurança** | jBCrypt |
| **IA no celular** | LiteRT 2.3 (TensorFlow Lite) |
| **Rede** | HttpURLConnection · AwesomeAPI |
| **Testes do app** | JUnit · Espresso · Accessibility Test Framework |
| **ML** | Python 3.12 · TensorFlow 2.21 · Keras 3 · NumPy · Pillow · Jupyter · pytest |

## Estrutura do repositório

```text
NotavelAI/
├── android/        app Android em Java (projeto do Android Studio)
├── ml/             modelo de ML em Python: dados, treino, avaliação e exportação
│   ├── src/        código do pipeline
│   ├── notebooks/  cada etapa explicada com tabelas e figuras
│   ├── reports/    métricas, histórico do treino e figuras
│   └── tests/      testes do pipeline e do contrato do modelo
└── docs/images/    diagramas deste README
```

| Onde | O quê |
| --- | --- |
| **Este repositório** | todo o código: `android/` e `ml/` |
| **[Hugging Face](https://huggingface.co/arthursilvadantas/NotavelAI)** | o modelo treinado (`.tflite` e `.keras`), com página, licença e exemplo de uso |
| **Fora do Git** | imagens de treino (`ml/data/`) e pesos do modelo |

## Como rodar

**App:** abra a pasta `android/` no Android Studio e clique em **Run ▶**. Na primeira compilação, o
Gradle baixa o modelo do Hugging Face sozinho. Pela linha de comando:

```bash
cd android
./gradlew assembleDebug        # APK em app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # testes que rodam no computador
```

**Modelo** (Python 3.12):

```bash
cd ml
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python -r requirements.txt
.venv/bin/python -m pytest
.venv/bin/python -m src.models.predict foto.jpg    # classifica uma foto
```

## Créditos e licenças

| Fonte | Uso no projeto | Licença |
| --- | --- | --- |
| [Cédulas do Real](https://www.kaggle.com/datasets/karenalmeida340/cdulas-do-real) · Almeida et al. ([artigo, 2025](https://doi.org/10.5335/rbca.v17i1.15934)) | fotos reais de treino e teste | MIT |
| BRL Dataset · Roboflow Universe (Jlio Csar) | recortes de cédulas | CC BY 4.0 |
| [banknoteBrazil](https://github.com/joaofracasso/banknoteBrazil) · João Fracasso | fotos de treino | uso acadêmico |
| [COCO 2017](https://cocodataset.org/) | imagens sem cédula (classe `fundo`) | CC BY / CC BY-SA |
| [AwesomeAPI](https://docs.awesomeapi.com.br/) | cotação de moedas | API pública |

Por causa do banknoteBrazil, o **modelo** é distribuído só para **uso acadêmico e de pesquisa**
(ver a [licença no Hugging Face](https://huggingface.co/arthursilvadantas/NotavelAI/blob/main/LICENSE)).
