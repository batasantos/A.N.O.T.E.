<p align="center">
  <img src="docs/logo/anote-logo.png" alt="Logo do A.N.O.T.E." width="520">
</p>

O A.N.O.T.E. é um aplicativo Android que se apresenta como um bloco de notas comum e guarda, em seu interior, um pedido de socorro discreto. Ele foi pensado para mulheres em situação de violência doméstica, que muitas vezes não conseguem abrir um aplicativo de emergência sem que o agressor perceba.

Para quem observa o aparelho, o app mostra apenas uma lista de notas. O painel de emergência só é aberto quando se cria uma nota com um título combinado (por padrão, três pontos: `...`) e se toca em Concluído. Nesse caso, a nota não é salva e o painel aparece no lugar dela.

Este é o README detalhado do projeto. Um resumo está no [README da raiz do repositório](../README.md).

O trabalho foi desenvolvido na disciplina Projetos em Computação II, do curso de Ciências da Computação da Unesp, e é um complemento aos canais oficiais de atendimento (190 e Ligue 180), nunca um substituto.

## Funcionamento

### Contas e acesso

O aplicativo aceita várias contas no mesmo aparelho, cada uma com notas, contatos e gravações próprios. No primeiro acesso, o cadastro tem três passos: dados da conta (nome, e-mail e senha, ou "Continuar com o Google", que apenas preenche nome e e-mail), contatos de confiança e permissões. Ao final do primeiro passo, o app exibe uma única vez um **código de recuperação**. Depois do cadastro, um guia explica o uso do app e pode ser consultado novamente pelo menu do painel.

As senhas nunca são armazenadas. Guarda-se apenas um hash PBKDF2-HMAC-SHA256, com sal próprio e 310 mil iterações, dentro de um armazenamento criptografado (EncryptedSharedPreferences, AES-256, com chave no Android Keystore). Após 5 erros seguidos, o login é bloqueado por 30 segundos, e o tempo dobra a cada novo erro, até 15 minutos. O contador permanece mesmo que o app seja fechado.

Quem esquece a senha tem três caminhos: receber um **link de redefinição por e-mail** (se o Firebase estiver configurado), usar o **código de recuperação** ou, não dispondo de nenhum dos dois, apagar a conta e começar de novo. Nos dois primeiros casos, as notas são mantidas.

### Notas

As notas funcionam como em qualquer aplicativo do tipo: criar, editar, fixar, apagar, buscar e ordenar. O menu da lista oferece apenas itens comuns: perfil, alteração de senha, código de recuperação, ordenação, exclusão de todas as notas, "Sobre", sair da conta e excluir a conta. Nenhum deles faz referência à emergência.

### Painel de emergência

O painel reúne os seguintes recursos:

- **SOS silencioso:** envia por SMS, a todos os contatos, o link da localização e continua atualizando a posição a cada 5 minutos, por até 2 horas, até que a pessoa toque no cartão para parar. Se algum contato não receber a mensagem, o painel informa quem falhou e permite tentar de novo. O texto do SMS pode ser personalizado.
- **Gravação de áudio:** registra o ambiente por até 30 minutos, em pasta privada do app. Na tela Gravações, é possível ouvir, exportar, compartilhar e apagar os áudios, além de ativar uma cópia de segurança em uma pasta escolhida (por exemplo, sincronizada com o Google Drive).
- **Delegacia mais próxima:** abre o Google Maps com a rota a pé, de carro ou de ônibus.
- **Contatos de confiança**, com o botão Disparar, que avisa apenas uma pessoa.

O menu do painel contém: contatos, gravações, permissões, atalho de disparo rápido, bloqueio ao reabrir, mensagem do SOS, como usar o app, ajuda e telefones (180 e 190) e título de acesso.

### Disparo rápido e disparo com o app fechado

- **Volume:** nas telas de notas e na tela de bloqueio do app, apertar o volume para baixo três vezes em até dois segundos envia o SOS sem abrir o painel, e o celular vibra para confirmar. O atalho vem ligado e pode ser desligado pelo menu do painel.
- **Configurações rápidas e widget:** com o app fechado, o SOS pode ser enviado pelo atalho "Notas" das configurações rápidas (um toque) ou pelo widget "Notas" da tela inicial (dois toques em até três segundos, para evitar disparos acidentais). Ambos têm nome e ícone neutros e só existem se a pessoa os adicionar. Com o celular bloqueado, nunca abrem o painel.

### Discrição e segurança

- O painel e as telas de gravação não permitem captura de tela e aparecem em branco na lista de apps recentes. Eles se fecham quando a tela se apaga ou quando a pessoa sai do app.
- A notificação do envio diz apenas "Sincronizando…", sem som e invisível na tela bloqueada. O aviso de falha também usa texto neutro.
- O backup automático do Android está desligado, e a nota usada como gatilho nunca é gravada.
- O **bloqueio ao reabrir** é opcional e é ativado pelo menu do painel. Ele pede o desbloqueio do aparelho depois de 30 segundos fora do app.
- Recomenda-se trocar o título secreto padrão (`...`), por ser fácil de adivinhar.

## Como executar

1. Instale o Android Studio e abra a pasta do projeto (File, Open).
2. O Gradle 8.7 exige JDK 17 ou 21 e não funciona com o Java 25. Se aparecer o erro "Incompatible Gradle JVM", altere o Gradle JDK em Settings, Build Tools, Gradle, para o jbr-17 ou o jbr-21.
3. Instale o SDK do Android 14 (API 34), em Settings, Languages & Frameworks, Android SDK, caso ainda não esteja instalado.
4. Execute em um emulador ou em um celular com a depuração USB ativada. Os testes de unidade rodam com `.\gradlew test` (Windows) ou `./gradlew test`.

O emulador não envia SMS de verdade e o GPS é simulado. Para ver o alerta completo, use um celular e cadastre o próprio número como contato.

### Recuperação de senha por e-mail (opcional)

Usa o Firebase Authentication e só é ativada quando os três valores de `app/src/main/res/values/firebase.xml` estão preenchidos. Eles vêm do arquivo `google-services.json`, que não precisa fazer parte do projeto; o plugin do Google também não é utilizado.

1. No Firebase Console, crie um projeto e adicione um app Android com o pacote `br.unesp.anote`.
2. Em Authentication, ative o método E-mail/senha.
3. Copie para o `firebase.xml` a chave de API, o identificador do app e o identificador do projeto.

O servidor guarda apenas o e-mail e a senha (como hash). Notas, contatos e gravações nunca saem do aparelho. Se o Console permitir, convém tornar neutros o remetente e o assunto do e-mail de redefinição. No projeto desenvolvido, o Console não permitiu editar o modelo, e o e-mail chegou à pasta de spam, de modo que o código de recuperação continua sendo a alternativa mais confiável.

### Login com Google (opcional)

O botão só aparece se houver um Client ID do tipo **Web** em `app/src/main/res/values/google.xml`. Para criá-lo e fazer o login funcionar:

1. No Google Cloud Console, configure a tela de permissão OAuth e inclua os e-mails de teste.
2. Crie um ID de cliente OAuth do tipo **Android**, com o pacote `br.unesp.anote` e o SHA-1 da chave de depuração. Esse ID não vai para o código.
3. Crie um ID de cliente OAuth do tipo **Aplicativo da Web** e copie o ID (termina em `.apps.googleusercontent.com`) para o `google.xml`.

O erro 28444 ("Developer console is not set up correctly") costuma indicar que o ID colocado no `google.xml` é o do tipo Android, ou que o pacote ou o SHA-1 cadastrados não conferem. A chave secreta do cliente Web não é usada pelo app e não deve ser incluída no projeto.

## Estrutura do código

O código fica em `app/src/main/java/br/unesp/anote/`:

- `LoginActivity`, `OnboardingActivity`, `GuideActivity` e `LockActivity`: entrada, cadastro em três passos, guia de primeiro uso e bloqueio ao reabrir.
- `Accounts`, `Session`, `RecoveryCode`, `Cloud` e `GoogleSignIn`: contas, sessão ativa, código de recuperação, recuperação por e-mail (Firebase) e botão do Google.
- `MainActivity` e `NoteEditorActivity`: lista de notas e editor, que detecta o título secreto.
- `EmergencyActivity`, `ContactsActivity` e `RecordingsActivity`: o painel e suas telas, que herdam de `GuardedActivity` (proteções de privacidade).
- `SosService`: localização, envio de SMS com acompanhamento de falhas, repetição do envio e gravação em segundo plano.
- `QuickSos`, `TriggerActivity`, `SosTileService` e `SosWidget`: atalho de volume, tela invisível de disparo, atalho das configurações rápidas e widget.
- `Backup` e `Recordings`: cópia de segurança para a pasta escolhida e pasta privada dos áudios.
- `NotesDbHelper` e `SecurePrefs`: banco SQLite de cada conta e preferências criptografadas.
- `MenuDialogs`, `Ui` e `AnoteApp`: diálogos, aparência dos menus e controle do bloqueio.

Os testes de unidade estão em `app/src/test/`. Os scripts do banco de dados estão em `database/schema.sql` e o logo, em `docs/logo/`. A documentação completa e o registro de testes acompanham o trabalho.

## Diferenças em relação ao protótipo

O card da delegacia usa uma ilustração e abre o Google Maps, que calcula a rota. Mostrar endereço, distância e tempo dentro do app exigiria uma API de mapas com chave. O editor de notas oferece apenas os botões de fixar e excluir.

## Limitações

- A recuperação por e-mail só existe se o Firebase for configurado e exige internet. Sem ela, quem perde a senha e o código perde os dados da conta. Quem controla o e-mail da conta também consegue redefinir a senha, e o código de recuperação não tem esse risco.
- O e-mail de redefinição usa o remetente padrão do Firebase e pode ir para a pasta de spam.
- O atalho de volume funciona apenas dentro do app. Com o app fechado, é preciso usar o atalho das configurações rápidas ou o widget, e o comportamento deles com o celular bloqueado varia conforme o aparelho.
- Em alguns aparelhos e operadoras, o envio de SMS pode ser bloqueado, e, no modo de economia de energia, o Android pode atrasar os reenvios de localização.
- Uma gravação interrompida à força pode ficar incompleta. Os áudios são apagados se o app for desinstalado, e os arquivos copiados para a pasta de backup ficam sem a proteção do app.
- Notas e áudios não são criptografados.
- Os testes foram escritos, mas a maior parte ainda precisa ser executada em aparelho real e registrada.
