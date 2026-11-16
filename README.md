<p align="center">
  <img src="anote/docs/logo/anote-logo.png" alt="Logo do A.N.O.T.E." width="520">
</p>

# A.N.O.T.E.

**Aplicativo de Notificação Oculta e Transmissão de Emergência.**

O A.N.O.T.E. é um aplicativo Android que se apresenta como um bloco de notas comum e guarda, em seu interior, um pedido de socorro discreto. Foi pensado para mulheres em situação de violência doméstica, que muitas vezes não conseguem abrir um aplicativo de emergência sem que o agressor perceba.

## Como funciona

- **Fachada de notas.** Para quem observa o celular, o app é apenas uma lista de notas, com criação, edição, busca e ordenação.
- **Painel oculto.** Ao criar uma nota com um título combinado (por padrão, `...`) e tocar em Concluído, o painel de emergência é aberto no lugar de salvar a nota.
- **SOS silencioso.** Envia por SMS a localização aos contatos de confiança e repete o envio a cada 5 minutos, até a pessoa interromper.
- **Gravação de áudio** do ambiente, com cópia de segurança opcional em uma pasta escolhida.
- **Delegacia mais próxima.** Abre o Google Maps com a rota até a Delegacia de Defesa da Mulher mais próxima.
- **Disparo rápido.** O SOS também pode ser enviado pelo volume (três toques), por um atalho nas configurações rápidas ou por um widget, ambos com nome e ícone neutros.
- **Privacidade.** Sem captura de tela, fora da lista de apps recentes, com notificações neutras e bloqueio opcional ao reabrir o app.

## Dados e segurança

Os dados ficam no próprio aparelho, e cada conta tem os seus. As senhas são guardadas apenas como hash (PBKDF2), em armazenamento criptografado. A recuperação de senha pode ser feita por um código de recuperação ou, se configurado, por e-mail, com o Firebase Authentication. Notas, contatos e gravações nunca saem do aparelho.

## Tecnologias

| Item | Detalhe |
| --- | --- |
| Plataforma | Android 8.0 (API 26) ou superior |
| Linguagem | Kotlin |
| Interface | XML com Material 3 |
| Armazenamento | SQLite e EncryptedSharedPreferences |
| Serviços opcionais | Firebase Authentication (recuperação de senha) e Login com Google (preenchimento do cadastro) |

## Como executar

1. Abra a pasta [`anote`](anote) no Android Studio.
2. Use o JDK 17 ou 21 como Gradle JDK.
3. Execute em um emulador ou em um celular com a depuração USB ativada.

As instruções completas, incluindo a configuração do Firebase e do login com Google, estão no [README detalhado](anote/README.md).

## Estado do projeto

A versão atual é a 1.0. O código foi escrito e verificado por leitura, mas a maior parte dos testes manuais ainda precisa ser executada em aparelho real. O registro de testes e a documentação completa acompanham o trabalho.

## Contexto acadêmico

Trabalho de conclusão desenvolvido na disciplina Projetos em Computação II, do Bacharelado em Ciências da Computação da Unesp, com orientação de Alexandro Jose Baldassin. O projeto se alinha aos Objetivos de Desenvolvimento Sustentável 5 (Igualdade de Gênero), 9 (Indústria, Inovação e Infraestrutura) e 16 (Paz, Justiça e Instituições Eficazes).

## Aviso

O aplicativo é um complemento e não substitui os canais oficiais de atendimento. Em situação de perigo, ligue **190** (polícia) ou **180** (Central de Atendimento à Mulher).
