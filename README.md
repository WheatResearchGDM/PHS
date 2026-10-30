# PHS Reader — versão 0.3.0

Aplicativo Android para coletar avaliações de PHS em celular ou tablet. A tela de coleta usa um desenho proporcional de 380 × 800 unidades, ajustado e centralizado na área disponível, sem rolagem. As janelas auxiliares podem rolar quando necessário para acomodar o teclado e fontes maiores.

## Funções

- Importação de `.xlsx` e CSV UTF-8 com `FEID, ENSAIO, LOCAL, VARIEDADE, PARCELA, REPETIÇÃO` na primeira aba. A ordem das colunas pode mudar. Linhas com FEID ausente ou repetido bloqueiam a importação sem substituir o catálogo anterior.
- Atualização do arquivo de entrada já selecionado, preservando as avaliações existentes e a identificação original de cada coleta.
- QR Code offline com modelo ML Kit incluído no APK, leitura limitada ao quadrado central, e busca alternativa por número da parcela. Se uma parcela tiver vários resultados, o coletor escolhe a amostra.
- Contagens novas R/MR/MS/S/AS de 0 a 15. 0 e 2–9 avançam automaticamente; 1 aguarda outro dígito (10–15) ou seta para registrar somente 1. O avanço automático termina em AS. Campos vazios equivalem a zero. Total zero exige confirmação e produz média em branco. Registros históricos mantêm seus valores originais.
- Média ponderada `(R + MR*3 + MS*5 + S*7 + AS*9) / total`.
- Observações com Germinado, Doente, Verde e texto livre.
- Colheita e montagem opcionais, mantidas no mesmo dia até alteração e limpas no início de um novo dia; leitura corresponde ao dia em que a coleta é salva.
- Cadastro, seleção, edição e exclusão de usuários; apelido do aparelho e identificador exclusivo da instalação.
- SQLite local, uma avaliação por FEID/dia, com aviso antes de editar uma avaliação existente. O contador não aumenta ao editar.
- Salvar limpa o formulário e reativa a leitura da próxima etiqueta. A etiqueta recém-salva é bloqueada até sair do enquadramento, evitando aviso imediato de duplicidade. Reler permite abrir intencionalmente a mesma amostra para editar.
- Exportação de um lote diário em Excel ou CSV com as 20 colunas do modelo, nome `RESULTADO_PHS_dd-mm-aaaa_DISPOSITIVO.xlsx`.
- Limpar dados de coleta apaga somente avaliações locais. Preserva usuários, aparelho, catálogo, configurações e arquivos exportados.

## OneDrive

O destino é escolhido **somente ao clicar em Exportar**, depois da seleção de lote e formato. Não há seleção de destino nas configurações. As opções são **OneDrive direto**, **OneDrive instalado**, **Pasta no aparelho** e **Salvar como**.

**OneDrive direto:** a implementação usa Microsoft Graph e um fluxo de cliente público, sem segredo de cliente. Requer registrar o PHS Reader na Microsoft e informar o ID público do aplicativo. O registro ainda precisa ser feito na conta/organização; não é criado pelo APK. A autenticação acontece na página oficial da Microsoft. Depois o PHS Reader permite navegar pelas pastas, escolher uma e enviar o lote. A sessão de renovação é cifrada com Android Keystore. O envio só é confirmado após uma resposta de sucesso do Graph. Consulte [o roteiro de registro sem contratação de recursos pagos](docs/REGISTRO_MICROSOFT.md).

**OneDrive instalado:** usa a tela do aplicativo OneDrive do Android para escolher a pasta e confirmar o envio, sem registro Microsoft adicional. Abrir essa tela não confirma upload; confira a conclusão no OneDrive. **Pasta no aparelho/Salvar como:** usam os seletores do Android em cada exportação. A entrada continua podendo ser escolhida no OneDrive pelo seletor de arquivos. As avaliações permanecem no aparelho se a conexão ou o envio não forem concluídos.

As operações Graph usadas não constam como APIs cobradas por uso. O registro exige acesso a um diretório Entra existente e pode depender do administrador. A criação de um ambiente novo pode ter outros requisitos; não contrate recursos pagos para seguir este projeto. O espaço do OneDrive continua sujeito ao plano existente.

## Instalar

[Baixar PHS Reader 0.3.0 no GitHub](https://github.com/WheatResearchGDM/PHS/releases/download/v0.3.0/PHS-Reader-0.3.0.apk). O APK está em **Releases**, disponível independentemente dos artefatos temporários de Actions.

O instalador gerado em `dist/PHS-Reader-0.3.0.apk` mantém o pacote e a chave local de testes da versão 0.1.0 publicada em Releases. Instale por cima para preservar as coletas, sem desinstalar. Compatível com Android 8.0/API 26 ou superior, câmera traseira opcional. Conceda a permissão de câmera para ler QR Codes.

Para manter atualizações locais compatíveis, preserve `.tools/debug.keystore`. Ela não é enviada ao GitHub. Os APKs produzidos pelo CI usam uma chave de testes temporária e não substituem uma instalação assinada com outra chave. A versão de produção deve adotar uma chave de distribuição estável e protegida.

## Compilar no Windows

```powershell
./tools/build.ps1 -Prepare
```

Java 17, Gradle 8.11.1 e SDK Android 35 são preparados em `.tools`, dentro deste projeto. `local.properties`, ferramentas, caches, chaves e artefatos binários não são versionados. O script executa testes unitários, Android Lint, compilação do APK e verificação de assinatura.

Com Java e Android SDK previamente configurados, use também o Gradle Wrapper. É necessário ter `.tools/debug.keystore` ou ajustar a configuração de assinatura de testes.

O fluxo GitHub Actions compila e disponibiliza o APK de testes nos artefatos de cada execução.

## Validação

Os testes automatizados cobrem coleta, persistência, contagens, troca de dia, layout, CSV/Excel, destinos de exportação, intenção de compartilhamento Android e protocolo Graph com respostas simuladas: login pendente/expirado, renovação da sessão, paginação de pastas, rejeição de links externos, envio e conflitos de versão. Não houve autenticação nem envio real no OneDrive direto: essa validação depende do registro Microsoft e da autorização da conta.

O leitor do aplicativo foi conferido com o modelo real de 7.804 amostras. O Excel de saída foi conferido independentemente com openpyxl, incluindo 20 colunas, identificadores, datas, fórmulas e valores em cache. Android Lint é executado sem supressão de erros. A leitura de câmera, permissões reais e a integração específica do OneDrive precisam de validação nos celulares/tablets que serão usados no campo.

Os modelos com dados de pesquisa não são incorporados ao APK nem publicados neste repositório. O coletor seleciona a entrada no próprio aparelho.
