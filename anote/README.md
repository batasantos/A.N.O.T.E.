<p align="center">
  <img src="docs/logo/anote-logo.png" alt="Logo do A.N.O.T.E." width="520">
</p>

A.N.O.T.E. é um aplicativo Android que parece um bloco de notas comum, mas guarda dentro dele um pedido de socorro discreto. Pensei nele para mulheres em situação de violência doméstica, que muitas vezes não podem abrir um aplicativo de emergência sem que o agressor perceba.

Quem olhar por cima do ombro vê só uma lista de notas. Para chegar ao painel de emergência, é preciso criar uma nota com um título combinado (por padrão, três pontos: `...`) e tocar em Concluído. Em vez de salvar a nota, o app abre o painel.

## Como funciona

**Contas.** O app aceita várias contas no mesmo celular, cada uma com notas, contatos e gravações separados. No primeiro acesso a pessoa cria a conta em três passos: dados (nome, e-mail e senha, ou "Continuar com o Google" só para preencher nome e e-mail), contatos de confiança e permissões. No fim do primeiro passo o app mostra, uma única vez, um **código de recuperação**. Depois do cadastro aparece um **guia** de como o app funciona, que pode ser revisto pelo menu do painel.

As senhas nunca são guardadas: só um hash PBKDF2-HMAC-SHA256 com sal próprio (310 mil iterações). Todos os dados de conta ficam em EncryptedSharedPreferences (AES-256, chave no Android Keystore). Depois de 5 erros seguidos o login é bloqueado por 30 segundos, e o tempo dobra a cada novo erro, até 15 minutos; o contador sobrevive ao fechamento do app. Quem esquece a senha pode pedir um **link de redefinição por e-mail** (se o Firebase estiver configurado), usar o **código de recuperação** (as notas continuam nos dois casos) ou, sem nenhum dos dois, apagar a conta e recomeçar.

**Notas.** Funcionam como em qualquer app: criar, editar, fixar, apagar, buscar e ordenar. O menu (⋮) da lista tem **Meu perfil**, **Alterar senha**, **Código de recuperação**, **Ordenar notas**, **Apagar todas as notas**, **Sobre o app**, **Sair da conta** e **Excluir minha conta**. Nada ali fala de emergência, de propósito.

**Painel de emergência.** Abre quando a nota tem o título secreto. Tem:

- **SOS Silencioso:** manda um SMS com o link da localização para todos os contatos e **continua atualizando a cada 5 minutos** (até 2 horas) até a pessoa tocar no cartão para parar. Se algum contato não receber, o painel avisa quem falhou e deixa tentar de novo. A mensagem pode ser personalizada.
- **Gravação de áudio:** grava o ambiente (até 30 minutos) em pasta privada do app. Em **Gravações** dá para ouvir, exportar, compartilhar, apagar e ligar o **backup** para uma pasta escolhida (por exemplo, uma pasta do Google Drive).
- **Delegacia mais próxima:** abre no Google Maps a rota a pé, de carro ou de ônibus.
- **Contatos de confiança**, com o botão Disparar para avisar só uma pessoa.

O menu do painel tem: contatos, gravações, permissões, **atalho de disparo rápido**, **bloqueio ao reabrir**, **mensagem do SOS**, **como usar o app**, ajuda e telefones (180 e 190) e **título de acesso**.

**Atalho de disparo rápido.** Nas telas de notas (e na tela de bloqueio), apertar o volume para baixo 3 vezes em até 2 segundos envia o SOS sem abrir o painel; o celular vibra para confirmar. Pode ser desligado no painel.

**Disparo com o app fechado.** O atalho "Notas" das configurações rápidas (um toque) e o widget "Notas" da tela inicial (dois toques em até 3 segundos) enviam o SOS sem abrir o app. Os dois têm nome e ícone neutros. Com o celular bloqueado, nunca abrem o painel.

## Cuidados com a discrição e a segurança

O painel e as telas de gravação não aceitam captura de tela e aparecem em branco nos apps recentes; fecham sozinhos quando a tela apaga ou a pessoa sai do app. A notificação do envio diz só "Sincronizando…", sem som e invisível na tela bloqueada; o aviso de falha também usa texto neutro. O backup automático do Android está desligado. A nota usada como gatilho nunca é gravada. O **bloqueio ao reabrir** (opcional) pede o desbloqueio do aparelho depois de 30 segundos fora do app. Recomenda-se trocar o título secreto padrão (`...`), que é fácil de adivinhar.

## Como rodar

1. Instale o Android Studio e abra a pasta do projeto (File → Open).
2. O Gradle 8.7 precisa de JDK 17 ou 21 (não funciona com o 25). Se aparecer "Incompatible Gradle JVM", mude o Gradle JDK em Settings → Build Tools → Gradle para o jbr-17 ou jbr-21.
3. Instale o SDK do Android 14 (API 34) em Settings → Languages & Frameworks → Android SDK, se ainda não tiver.
4. Rode em um emulador ou em um celular com depuração USB ligada. Para rodar os testes de unidade: `.\gradlew test` (Windows) ou `./gradlew test`.

O emulador não manda SMS de verdade e o GPS é simulado. Para ver o alerta de ponta a ponta, use um celular e coloque o seu próprio número como contato.

**Recuperação de senha por e-mail (opcional).** Usa o Firebase Authentication e só é ativada se os três valores de `app/src/main/res/values/firebase.xml` estiverem preenchidos (vêm do `google-services.json`; as instruções estão no próprio arquivo). Para ativar: crie um projeto no Firebase Console, adicione um app Android com o pacote `br.unesp.anote`, ative Authentication, método E-mail/senha, e preencha o `firebase.xml`. Em Authentication, Templates, deixe o remetente e o assunto do e-mail de redefinição neutros (por exemplo, "Notas" e "Confirme sua conta") para o e-mail não revelar o app. O servidor guarda só o e-mail e a senha (como hash); notas, contatos e gravações nunca saem do celular.

**Login com Google (opcional).** O botão só aparece se houver um Client ID web em `app/src/main/res/values/google.xml`. Ele é criado no Google Cloud Console; para o login funcionar também é preciso registrar o SHA-1 do app (`.\gradlew signingReport`).

## Onde está cada coisa

O código fica em `app/src/main/java/br/unesp/anote/`:

- `LoginActivity`, `OnboardingActivity`, `GuideActivity` e `LockActivity`: entrada, cadastro em três passos, guia de primeiro uso e bloqueio ao reabrir.
- `Accounts`, `Session`, `RecoveryCode`, `Cloud` e `GoogleSignIn`: contas, sessão ativa, código de recuperação, recuperação por e-mail (Firebase) e o botão do Google.
- `MainActivity` e `NoteEditorActivity`: lista de notas e editor, que detecta o título secreto.
- `EmergencyActivity`, `ContactsActivity` e `RecordingsActivity`: o painel e suas telas; herdam de `GuardedActivity` (proteções de privacidade).
- `SosService`: localização, SMS com acompanhamento de falha, envio repetido e gravação em segundo plano.
- `QuickSos`, `Backup` e `Recordings`: atalho de disparo rápido, backup para pasta escolhida e pasta privada dos áudios.
- `NotesDbHelper` e `SecurePrefs`: banco SQLite de cada conta e preferências criptografadas.
- `MenuDialogs`, `Ui` e `AnoteApp`: diálogos, visual dos menus e controle do bloqueio.

Os testes de unidade estão em `app/src/test/`. Os scripts do banco estão em `database/schema.sql` e o logo em `docs/logo/`.

## O que ficou diferente do protótipo

O card da delegacia usa uma ilustração e abre o Google Maps, que calcula a rota (endereço, distância e tempo exigiriam uma API de mapas com chave). O editor de notas tem só fixar e excluir.

## Limitações

O app funciona sem servidor. A recuperação por e-mail só existe se o Firebase for configurado e exige internet; sem isso, quem perde a senha e o código perde os dados da conta. Em alguns aparelhos e operadoras o envio de SMS pode ser bloqueado, e no modo de economia de energia o Android pode atrasar os reenvios de localização. Uma gravação interrompida à força pode ficar incompleta. Os áudios são apagados se o app for desinstalado (por isso existe o backup), e os arquivos copiados para a pasta de backup ficam sem a proteção do app. O app é um complemento: em perigo, os canais oficiais continuam sendo o 190 e o Ligue 180.
