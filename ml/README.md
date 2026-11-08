# NotavelAI · Brazilian Real banknote recognition

**🤗 Model on Hugging Face / Modelo no Hugging Face: <https://huggingface.co/arthursilvadantas/NotavelAI>**

This folder holds the code that builds the model; the [Android app](../android/README.md) is in
`../android`. / Esta pasta tem o código que gera o modelo; o [app Android](../android/README.md)
fica em `../android`.

**[English](#english) · [Português](#português) · [Code / Código](#code--código)**

<a id="english"></a>

## English

NotavelAI is an Android app for blind and visually impaired people: the user takes a photo of a
banknote and the app **speaks its value out loud**. This repository holds the computer vision model
that runs inside the app, **offline, on the phone**.

| | |
| --- | --- |
| **Task** | image classification: which Brazilian Real banknote is in the photo, if any |
| **Classes** | R$ 2, 5, 10, 20, 50, 100, 200 and `fundo` (no Real banknote) |
| **Model** | average of two fine-tuned MobileNetV2 networks |
| **Formats** | TensorFlow Lite float32 (`.tflite`, used by the app) and Keras (`.keras`) |
| **Size on device** | 2 × 8.9 MB |
| **License** | academic and research use only ([why](#license)) |
| **Status** | academic prototype; not reliable as the only way to check money |

Built for the Mobile Programming course at UFMS (Brazil), 2026, by the DreamTeam group.

### Files on Hugging Face

```text
├── tflite/
│   ├── notavelai_base.tflite      member 1, for Android / LiteRT
│   └── notavelai_external.tflite  member 2, for Android / LiteRT
├── keras/
│   ├── notavelai_base.keras       member 1, for Python (retraining, fine-tuning)
│   └── notavelai_external.keras   member 2, for Python
├── evaluation/
│   ├── evaluation.csv             accuracy and announced values per model and set
│   └── phone_predictions.csv      final model answer for each phone photo
├── images/training_curves.png     training curves (notavelai_external)
├── labels.txt                     the 8 classes in output order
├── classes.json                   same order, as JSON
├── predict.py                     ready-to-run example
└── LICENSE
```

### Quick start

```bash
pip install tensorflow pillow numpy huggingface_hub
hf download arthursilvadantas/NotavelAI --local-dir NotavelAI
python NotavelAI/predict.py photo.jpg
# photo.jpg: R$ 20 (96%)
```

`predict.py` reproduces the app: it averages both members and only announces a value at 50%
confidence or more. Below that it answers "not sure"; for someone who cannot see the banknote,
hearing a wrong value is worse than hearing nothing.

### How it works

```text
photo ─► fix EXIF rotation ─► resize keeping aspect ratio, pad with black to 224×224
      ─► notavelai_base ────┐
      ─► notavelai_external ─┴─► average probabilities ─► most likely class
```

- **Input:** `[1, 224, 224, 3]`, RGB, float32, values **0–255, not normalized** (scaling to
  [-1, 1] is inside the model).
- **Output:** `[1, 8]`, float32 probabilities in the order of `labels.txt`:
  `2, 5, 10, 20, 50, 100, 200, fundo`.
- **Preprocessing matters:** replicate `tf.image.resize_with_pad` (bilinear, no antialias,
  centered). A resize shifted by about 1 pixel already changes predictions.
- **Why two models:** each member makes different mistakes; the average is the one that speaks the
  fewest wrong values across validation and phone photos.

### Training

Both members share the same recipe; only the data differs.

| Step | Setting |
| --- | --- |
| Backbone | MobileNetV2 pre-trained on ImageNet, 224×224 input |
| Head | global average pooling, dropout 0.3, dense softmax with 8 outputs |
| Phase 1 | frozen backbone, head only; Adam, learning rate 1e-3, up to 30 epochs |
| Phase 2 | fine-tune the last 30 layers; learning rate 1e-5, up to 30 epochs |
| Stopping | early stopping, patience 6; keeps the lowest `val_loss` |
| Augmentation | rotation, zoom, horizontal flip, brightness, contrast (not in the exported model) |
| Batch | 32 |
| Class imbalance | class weights when the largest class exceeds 1.5× the smallest |

| Member | Training data |
| --- | --- |
| `notavelai_base` | BRL Dataset crops, `fundo` class, first batch of phone photos, composites (banknotes pasted on scenes) |
| `notavelai_external` | everything above, plus a second batch of phone photos (US dollar bills as `fundo`) and 500 banknoteBrazil photos (R$ 2 to R$ 50) |

![Training curves](reports/figures/training_curves.png)

**Data sources**

- **BRL Dataset** (Roboflow Universe, author Jlio Csar),
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/): annotated photos, cropped with a 10%
  margin. Duplicates across splits were removed by hash.
- **`fundo` class:** background regions from the same dataset and [COCO 2017](https://cocodataset.org/)
  images under CC BY or CC BY-SA.
- **[banknoteBrazil](https://github.com/joaofracasso/banknoteBrazil)** (João Fracasso), released
  "for academic research use only". Training only, 100 photos per banknote.
- **Phone photos** taken by the group and labeled by hand. Photos of the same scene never appear in
  both training and test.

### Results

- **Validation:** 66 crops from the BRL Dataset.
- **Phone:** 24 full photos taken with a phone, from scenes not seen in training. Closest to real use.

"Speaks right / wrong" counts the times the app would announce a value (confidence ≥ 50% and class
other than `fundo`).

| Model | Validation | Validation: speaks wrong | Phone | Phone: speaks right | Phone: speaks wrong |
| --- | ---: | ---: | ---: | ---: | ---: |
| `notavelai_base` only | 64/66 | 1 | 14/24 | 10 | 5 |
| `notavelai_external` only | 60/66 | 4 | 16/24 | 11 | 4 |
| **final model (average)** | 63/66 | 1 | 13/24 | **11** | **4** |

On the 24 phone photos the final model **announces 15 values, 11 right and 4 wrong**, and stays
silent on the other 9. Neither US dollar photo triggers an announcement.

| True banknote | App said | Confidence |
| --- | --- | ---: |
| R$ 5 (first series) | R$ 10 | 89% |
| R$ 5 (first series) | R$ 10 | 56% |
| R$ 20 | R$ 50 | 52% |
| R$ 200 | R$ 100 | 51% |

**Latency:** about 4 ms per photo for both members together on an Apple Silicon Mac CPU, 1 thread.
Not yet measured on a phone.

### Limitations

- **Small test set:** only 24 phone photos, which also guided the model choice; numbers are likely
  optimistic.
- **Under-tested classes:** a single R$ 2 photo and no R$ 50 in the phone test.
- **Known confusions:** first-series R$ 5 vs R$ 10, R$ 20 vs R$ 50, R$ 200 vs R$ 100.
- **One banknote per photo:** with several notes in the frame, the answer is unpredictable.
- **Foreign currency:** only US dollars were seen as `fundo`; other currencies were not tested.
- **Counterfeits:** the model does not check authenticity.

### Intended use

- **Suitable:** research, teaching and assistive technology prototypes.
- **Not suitable:** the only way for a person to check money, cash registers, vending machines, or
  any commercial use.

### License

**Academic and research use only** (see [`LICENSE`](https://huggingface.co/arthursilvadantas/NotavelAI/blob/main/LICENSE)). One member (`notavelai_external`)
was trained on banknoteBrazil, released by its author for academic research only. The BRL Dataset
and COCO images are CC BY / CC BY-SA and require attribution, given above.

---

<a id="português"></a>

## Português

O NotavelAI é um app Android para pessoas cegas ou com baixa visão: a pessoa fotografa uma cédula e
o app **fala o valor em voz alta**. Este repositório tem o modelo de visão computacional que roda
dentro do app, **offline, no próprio celular**.

| | |
| --- | --- |
| **Tarefa** | classificação de imagem: qual cédula de Real está na foto, ou nenhuma |
| **Classes** | R$ 2, 5, 10, 20, 50, 100, 200 e `fundo` (sem cédula de Real) |
| **Modelo** | média de duas MobileNetV2 com fine-tuning |
| **Formatos** | TensorFlow Lite float32 (`.tflite`, usado pelo app) e Keras (`.keras`) |
| **Tamanho no celular** | 2 × 8,9 MB |
| **Licença** | somente uso acadêmico e de pesquisa ([por quê](#licença)) |
| **Estado** | protótipo acadêmico, não confiável como única forma de conferir dinheiro |

Projeto da disciplina de Programação Mobile da UFMS, 2026, grupo DreamTeam.

### Arquivos no Hugging Face

```text
├── tflite/
│   ├── notavelai_base.tflite      membro 1, para Android / LiteRT
│   └── notavelai_external.tflite  membro 2, para Android / LiteRT
├── keras/
│   ├── notavelai_base.keras       membro 1, para Python (retreino, fine-tuning)
│   └── notavelai_external.keras   membro 2, para Python
├── evaluation/
│   ├── evaluation.csv             acertos e valores anunciados por modelo e conjunto
│   └── phone_predictions.csv      resposta do modelo final para cada foto do celular
├── images/training_curves.png     curvas de treino (notavelai_external)
├── labels.txt                     as 8 classes na ordem das saídas
├── classes.json                   a mesma ordem, em JSON
├── predict.py                     exemplo pronto para rodar
└── LICENSE
```

### Início rápido

```bash
pip install tensorflow pillow numpy huggingface_hub
hf download arthursilvadantas/NotavelAI --local-dir NotavelAI
python NotavelAI/predict.py foto.jpg
# foto.jpg: R$ 20 (96%)
```

O `predict.py` reproduz o app: faz a média dos dois membros e só anuncia um valor com pelo menos 50%
de confiança. Abaixo disso responde "não consegui identificar": para quem não enxerga a nota, ouvir
um valor errado é pior do que não ouvir nada.

### Como funciona

```text
foto ─► endireita pela EXIF ─► redimensiona sem esticar, completa com preto até 224×224
     ─► notavelai_base ────┐
     ─► notavelai_external ─┴─► média das probabilidades ─► classe mais provável
```

- **Entrada:** `[1, 224, 224, 3]`, RGB, float32, valores **0–255, sem normalizar** (a conversão
  para [-1, 1] está dentro do modelo).
- **Saída:** `[1, 8]`, probabilidades float32 na ordem de `labels.txt`:
  `2, 5, 10, 20, 50, 100, 200, fundo`.
- **O pré-processamento importa:** reproduza `tf.image.resize_with_pad` (bilinear, sem antialias,
  centralizado). Um redimensionamento deslocado em cerca de 1 pixel já muda respostas.
- **Por que dois modelos:** cada membro erra em situações diferentes; a média é a que menos fala
  valores errados, somando validação e fotos de celular.

### Treino

Os dois membros usam a mesma receita; só os dados mudam.

| Etapa | Configuração |
| --- | --- |
| Base | MobileNetV2 pré-treinada no ImageNet, entrada 224×224 |
| Cabeça | global average pooling, dropout 0,3, camada densa softmax com 8 saídas |
| Fase 1 | base congelada, treina só a cabeça; Adam, learning rate 1e-3, até 30 épocas |
| Fase 2 | fine-tuning das últimas 30 camadas; learning rate 1e-5, até 30 épocas |
| Parada | early stopping, paciência 6; guarda o menor `val_loss` |
| Augmentation | rotação, zoom, espelhamento horizontal, brilho e contraste (fora do modelo exportado) |
| Lote | 32 |
| Desbalanceamento | pesos por classe quando a maior classe passa de 1,5× a menor |

| Membro | Dados de treino |
| --- | --- |
| `notavelai_base` | recortes do BRL Dataset, classe `fundo`, primeiro lote de fotos de celular e composições (cédulas coladas sobre cenas) |
| `notavelai_external` | tudo acima, mais um segundo lote de fotos de celular (notas de dólar como `fundo`) e 500 fotos do banknoteBrazil (R$ 2 a R$ 50) |

**Fontes dos dados**

- **BRL Dataset** (Roboflow Universe, autor Jlio Csar),
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/): fotos anotadas, recortadas com 10% de
  margem. Duplicatas entre os conjuntos foram removidas por hash.
- **Classe `fundo`:** regiões sem cédula do próprio dataset e imagens do
  [COCO 2017](https://cocodataset.org/) com licença CC BY ou CC BY-SA.
- **[banknoteBrazil](https://github.com/joaofracasso/banknoteBrazil)** (João Fracasso), liberado
  "for academic research use only". Só no treino, 100 fotos por cédula.
- **Fotos de celular** tiradas pelo grupo e rotuladas à mão. Fotos da mesma cena nunca ficam em
  treino e teste ao mesmo tempo.

### Resultados

- **Validação:** 66 recortes do BRL Dataset.
- **Celular:** 24 fotos inteiras tiradas com celular, de cenas fora do treino. É o mais parecido com
  o uso real.

"Fala certo / errado" conta as vezes em que o app anunciaria um valor (confiança ≥ 50% e classe
diferente de `fundo`).

| Modelo | Validação | Validação: fala errado | Celular | Celular: fala certo | Celular: fala errado |
| --- | ---: | ---: | ---: | ---: | ---: |
| só `notavelai_base` | 64/66 | 1 | 14/24 | 10 | 5 |
| só `notavelai_external` | 60/66 | 4 | 16/24 | 11 | 4 |
| **modelo final (média)** | 63/66 | 1 | 13/24 | **11** | **4** |

Nas 24 fotos de celular, o modelo final **anuncia 15 valores, 11 certos e 4 errados**, e fica em
silêncio nas outras 9. Nenhuma das fotos de dólar gera anúncio de valor.

| Cédula real | App falou | Confiança |
| --- | --- | ---: |
| R$ 5 (primeira família) | R$ 10 | 89% |
| R$ 5 (primeira família) | R$ 10 | 56% |
| R$ 20 | R$ 50 | 52% |
| R$ 200 | R$ 100 | 51% |

**Latência:** cerca de 4 ms por foto para os dois membros juntos, numa CPU de Mac (Apple Silicon),
com 1 thread. Ainda não foi medida em celular.

### Limitações

- **Teste pequeno:** só 24 fotos de celular, que também orientaram a escolha do modelo; os números
  tendem a ser otimistas.
- **Classes pouco testadas:** só 1 foto de R$ 2 e nenhuma de R$ 50 no teste de celular.
- **Confusões conhecidas:** R$ 5 da primeira família com R$ 10, R$ 20 com R$ 50, R$ 200 com R$ 100.
- **Uma cédula por foto:** com várias notas na imagem, a resposta é imprevisível.
- **Moeda estrangeira:** só o dólar foi visto como `fundo`; outras moedas não foram testadas.
- **Notas falsas:** o modelo não verifica autenticidade.

### Uso recomendado

- **Adequado:** pesquisa, ensino e protótipos de tecnologia assistiva.
- **Não adequado:** como única forma de uma pessoa conferir dinheiro, em caixas, máquinas ou
  qualquer uso comercial.

<a id="licença"></a>

### Licença

**Somente uso acadêmico e de pesquisa** (ver [`LICENSE`](https://huggingface.co/arthursilvadantas/NotavelAI/blob/main/LICENSE)). Um dos membros
(`notavelai_external`) foi treinado com o banknoteBrazil, que o autor libera só para pesquisa
acadêmica. O BRL Dataset e as imagens do COCO são CC BY / CC BY-SA e exigem atribuição, feita acima.

---

<a id="code--código"></a>

## Code / Código

Python 3.12. Commands run inside `ml/`. / Os comandos rodam dentro de `ml/`.

```text
ml/
├── config/     hand-made decisions: photo rotation, phone photo labels / decisões feitas à mão
├── data/       images, not in Git / imagens, fora do Git
├── models/     final model and class order / modelo final e ordem das classes
├── notebooks/  01_data, 02_training, 03_evaluation_export
├── reports/    counts, training history, evaluation, figures / relatórios e figuras
├── src/        data/ (preparation / preparação) and models/ (training, evaluation, export)
└── tests/
```

```bash
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python -r requirements.txt
.venv/bin/python -m pytest

.venv/bin/python -m src.data.prepare_crops         # banknote crops and splits / recortes e splits
.venv/bin/python -m src.data.build_background      # fundo class / classe fundo
.venv/bin/python -m src.data.prepare_own_photos    # phone photos / fotos do celular
.venv/bin/python -m src.data.prepare_external      # banknoteBrazil
.venv/bin/python -m src.data.build_composites      # composites / composições
.venv/bin/python -m src.models.train_model         # training / treino
.venv/bin/python -m src.models.ensemble            # .tflite + labels.txt
.venv/bin/python -m src.models.evaluate            # reports/evaluation.csv
.venv/bin/python -m src.models.export_android      # copy model to ../android / copia para o app
```

### Data splits / Divisão dos dados

| Set / Conjunto | Folder / Pasta | Images | Purpose / Uso |
| --- | --- | ---: | --- |
| train / treino | `data/processed/train/` | 1,571 | learning / aprendizado |
| validation / validação | `data/processed/valid/` | 66 | best epoch, validation metric / melhor época e métrica |
| dataset test / teste do dataset | `data/processed/test/` | 73 | reserved, not used yet / reservado |
| phone test / teste do celular | `data/phone_test/` | 24 | main metric, real photos / métrica principal |

- **BRL Dataset:** Roboflow's own split; duplicates are kept only in the most protected split
  (test > valid > train). / Divisão do próprio Roboflow; duplicatas ficam só no split mais protegido.
- **Phone photos / Fotos do celular:** split by scene in `config/phone_photo_labels.csv` (44 train,
  24 test). / Separadas por cena (44 treino, 24 teste).
- **banknoteBrazil, composites / composições:** training only. / Só no treino.
