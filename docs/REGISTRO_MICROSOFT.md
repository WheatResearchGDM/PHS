# Ativar o OneDrive direto no PHS Reader

A versão 0.3.0 prepara a conexão direta com Microsoft Graph. Ela **ainda precisa de um registro do aplicativo na sua conta/organização**. Instalar o APK não cria esse registro nem autoriza acesso ao seu OneDrive.

## Custo e acesso

As operações usadas são listar pastas, consultar arquivo e enviar arquivo. Elas não constam na lista de APIs medidas/cobradas do Microsoft Graph. O aplicativo não cria servidor, banco de dados Azure, armazenamento Azure pago nem usa APIs de rotulagem de sensibilidade cobradas. O espaço e a licença do seu OneDrive continuam sujeitos ao plano que você já tem.

O registro depende de acesso a um diretório Microsoft Entra existente e de permissão para registrar aplicativos. Isso pode ser feito pelo administrador do Microsoft 365 da organização. A criação de um novo diretório/conta Azure pode ter requisitos adicionais: **não contrate assinatura, não forneça cartão e não ative recursos pagos para seguir este roteiro**. Se o portal pedir isso, interrompa e solicite o registro ao administrador. Não é possível garantir um novo ambiente gratuito para qualquer conta pessoal.

## Registro pelo administrador

1. Entre em [Microsoft Entra](https://entra.microsoft.com/) com a conta da organização e selecione o diretório correto.
2. Abra **Aplicativos → Registros de aplicativos → Novo registro**. O nome é **PHS Reader**.
3. Para a configuração atual, escolha **Contas em qualquer diretório organizacional e contas Microsoft pessoais**, se a política da organização permitir. O aplicativo usa a autoridade Microsoft `common`.
4. Conclua o registro e copie **ID do aplicativo (cliente)**. Esse é um identificador público, não uma senha.
5. Em **Autenticação → Configurações avançadas**, habilite **Permitir fluxos de cliente público**. Usamos o fluxo de código de dispositivo. Não há necessidade de URI de redirecionamento Android nem de segredo do cliente nesse fluxo.
6. Em **Permissões de API → Adicionar permissão → Microsoft Graph → Permissões delegadas**, escolha **Files.ReadWrite**. Autorize o consentimento conforme a política da organização. O fluxo pede também `offline_access` para renovar a sessão.
7. Não crie segredo de cliente e não escolha permissões de aplicativo/daemon. Não são necessárias `Files.ReadWrite.All` nem `Sites.ReadWrite.All`.
8. Se a organização bloquear esse tipo de registro ou o fluxo de código de dispositivo, o administrador precisará avaliar a política. Não tente contornar as restrições.

## Conectar no Android

1. Abra **Exportar**, escolha dia e formato, e toque em **Exportar**.
2. Escolha **OneDrive · conexão direta Microsoft**.
3. Cole o **ID do aplicativo (cliente)**. Nenhuma senha ou segredo deve ser informado no PHS Reader.
4. O aplicativo mostra um código temporário. Toque em **Copiar código e abrir Microsoft**.
5. Na página oficial da Microsoft, informe o código, entre na conta e confira que o pedido é do **PHS Reader** com acesso aos seus arquivos.
6. Retorne ao PHS Reader e navegue pelas pastas. **Usar esta pasta** exporta o lote para a pasta escolhida. Havendo arquivo com o mesmo nome, o aplicativo pede confirmação de substituição.

A pasta é escolhida em cada exportação. A conexão pode ser reutilizada: o token de renovação é cifrado com uma chave do Android Keystore e não é gravado em texto puro. Para mudar a conta, use **Trocar conta** na janela de pastas.

Até concluir o registro, continuam disponíveis **OneDrive instalado**, **Pasta no aparelho** e **Salvar como**. As coletas permanecem no banco local quando o login ou o envio não é concluído.

## Referências oficiais

- [Registrar aplicativo Microsoft](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app)
- [Fluxo OAuth de código de dispositivo](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code)
- [Envio de arquivos pelo Microsoft Graph](https://learn.microsoft.com/en-us/graph/api/driveitem-put-content)
- [APIs cobradas por uso](https://learn.microsoft.com/en-us/graph/metered-api-list)
