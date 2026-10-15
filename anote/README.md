# A.N.O.T.E.

A.N.O.T.E. é um aplicativo Android que parece um bloco de notas comum, mas guarda dentro dele um pedido de socorro discreto. Pensei nele para mulheres em situação de violência doméstica, que muitas vezes não podem abrir um aplicativo de emergência sem que o agressor perceba.

Quem olhar por cima do ombro vê só uma lista de notas. Para chegar ao painel de emergência, é preciso criar uma nota com um título combinado (por padrão, três pontos: `...`) e tocar em Concluído. Em vez de salvar a nota, o app abre o painel.

## Como funciona

Na primeira vez que abre, o app pede três coisas, uma de cada vez: um cadastro rápido (nome e e-mail), pelo menos um contato de confiança e as permissões de localização, microfone, SMS e notificações. Depois disso ele abre direto nas notas e nunca mais mostra essas telas.

As notas funcionam como em qualquer app: dá para criar, editar, fixar, apagar, buscar e mudar a ordem. Tudo fica salvo no próprio celular.

O painel de emergência tem quatro coisas:

- **SOS Silencioso**, que manda um SMS com o link da localização para todos os contatos. A mensagem começa com o nome cadastrado.
- **Gravação de áudio**, que grava o ambiente. O arquivo fica guardado dentro do app, sem aparecer na galeria.
- **Delegacia mais próxima**, com atalhos para abrir no Google Maps a rota a pé, de carro ou de ônibus até a Delegacia de Defesa da Mulher mais próxima.
- **Contatos de confiança**, onde dá adicionar mais gente, remover (segurando o dedo no contato) ou usar o botão Disparar para avisar só uma pessoa.

No rodapé do painel também dá para trocar o título secreto.

## Cuidados com a discrição

Algumas escolhas foram feitas só para o app passar despercebido. O painel não aceita captura de tela e aparece em branco na lista de apps recentes. Ele se fecha sozinho quando a tela apaga ou quando a pessoa sai do app. A notificação que aparece durante o envio diz só "Sincronizando…", sem som e sem aparecer na tela de bloqueio. O backup automático do Android está desligado, e o título secreto, os contatos e o cadastro ficam criptografados. A nota usada como gatilho nunca é gravada no banco.

## Como rodar

1. Instale o Android Studio e abra a pasta do projeto (File → Open).
2. Espere o Gradle terminar de sincronizar. Se aparecer o erro "Invalid Gradle JDK configuration", clique em "Use Embedded JDK".
3. Instale o SDK do Android 14 (API 34) em Settings → Languages & Frameworks → Android SDK, se ainda não tiver.
4. Rode em um emulador ou em um celular com depuração USB ligada.

Dá para testar quase tudo no emulador, mas ele não manda SMS de verdade e o GPS é simulado (menu de três pontinhos → Location). Para ver o alerta funcionando de ponta a ponta, use um celular e coloque o seu próprio número como contato.

Para ver o primeiro acesso de novo, limpe os dados do app em Configurações → Apps → A.N.O.T.E. → Armazenamento.

## Onde está cada coisa

O código fica em `app/src/main/java/br/unesp/anote/`. Os arquivos principais são:

- `OnboardingActivity`: os três passos do primeiro acesso.
- `MainActivity` e `NoteEditorActivity`: a lista de notas e o editor, que também detecta o título secreto.
- `EmergencyActivity`: o painel de emergência.
- `SosService`: o serviço que pega a localização, manda os SMS e grava o áudio em segundo plano.
- `NotesDbHelper`: o banco SQLite das notas.
- `SecurePrefs`: onde ficam, criptografados, o cadastro, os contatos e o título secreto.

Os scripts do banco (criação das tabelas e exemplos de inserção, consulta, atualização e exclusão) estão em `database/schema.sql`.

## O que ficou diferente do protótipo

No protótipo, o card da delegacia mostra endereço, distância e tempo de trajeto. Para isso eu precisaria de uma API de mapas com chave de acesso, então por enquanto o card usa uma ilustração e os botões abrem o Google Maps, que calcula a rota de verdade.

O editor de notas tem só os botões de fixar e excluir. Negrito, itálico e os outros ícones do protótipo não foram feitos. Também não existe login com senha: o app não tem servidor, então o cadastro serve só para personalizar a mensagem de alerta.

## Limitações

Em alguns aparelhos e operadoras o envio de SMS pode ser bloqueado, por isso vale testar em mais de um celular. Ainda não existe uma tela para ouvir os áudios gravados. E o app é um complemento: em perigo, os canais oficiais continuam sendo o 190 e o Ligue 180.
