# PHS Reader — versão 0.1.0

Aplicativo Android para coletar avaliações de PHS em celular ou tablet. A tela de coleta usa um desenho proporcional de 380 × 800 unidades, ajustado e centralizado na área disponível, sem rolagem. As janelas auxiliares podem rolar quando necessário para acomodar o teclado e fontes maiores.

## Funções

- Importação de `.xlsx` e CSV UTF-8 com `FEID, ENSAIO, LOCAL, VARIEDADE, PARCELA, REPETIÇÃO` na primeira aba. A ordem das colunas pode mudar. Linhas com FEID ausente ou repetido bloqueiam a importação sem substituir o catálogo anterior.
- Atualização do arquivo de entrada já selecionado, preservando as avaliações existentes e a identificação original de cada coleta.
- QR Code offline com modelo ML Kit incluído no APK, leitura limitada ao quadrado central, e busca alternativa por número da parcela. Se uma parcela tiver vários resultados, o coletor escolhe a amostra.
- Contagens R/MR/MS/S/AS de 0 a 99. Campos vazios equivalem a zero. Total zero exige confirmação e produz média em branco.
- Média ponderada `(R + MR*3 + MS*5 + S*7 + AS*9) / total`.
- Observações com Germinado, Doente, Verde e texto livre.
- Colheita e montagem opcionais, mantidas até alteração; leitura corresponde ao dia em que a coleta é salva.
- Cadastro, seleção, edição e exclusão de usuários; apelido do aparelho e identificador exclusivo da instalação.
- SQLite local, uma avaliação por FEID/dia, com aviso antes de editar uma avaliação existente. O contador não aumenta ao editar.
- Exportação de um lote diário em Excel ou CSV com as 20 colunas do modelo, nome `RESULTADO_PHS_dd-mm-aaaa_DISPOSITIVO.xlsx`.
- Limpar dados de coleta apaga somente avaliações locais. Preserva usuários, aparelho, catálogo, configurações e arquivos exportados.

## OneDrive

Instale e autentique o OneDrive no Android. O aplicativo usa o seletor de documentos do sistema: escolha o arquivo de entrada e uma pasta de saída, quando o provedor oferecer seleção de pastas. Alguns provedores não permitem conceder acesso persistente a uma pasta; nesse caso a exportação oferece **Salvar como**, onde é possível escolher o OneDrive.

O sucesso da gravação significa que o arquivo foi entregue ao provedor Android. A conclusão do envio para a nuvem deve ser conferida no OneDrive. Não há autenticação Microsoft Graph própria nem sincronização automática em segundo plano nesta versão. As avaliações permanecem locais até a exportação solicitada pelo usuário.

## Instalar

O instalador gerado em `dist/PHS-Reader-0.1.0.apk` é assinado com uma chave local de testes. Copie para o Android e abra o APK, autorizando a instalação por essa origem quando o sistema solicitar. Compatível com Android 8.0/API 26 ou superior, câmera traseira opcional. Conceda a permissão de câmera para ler QR Codes.

Para manter atualizações locais compatíveis, preserve `.tools/debug.keystore`. Ela não é enviada ao GitHub. Os APKs produzidos pelo CI usam uma chave de testes temporária e não substituem uma instalação assinada com outra chave. A versão de produção deve adotar uma chave de distribuição estável e protegida.

## Compilar no Windows

```powershell
./tools/build.ps1 -Prepare
```

Java 17, Gradle 8.11.1 e SDK Android 35 são preparados em `.tools`, dentro deste projeto. `local.properties`, ferramentas, caches, chaves e artefatos binários não são versionados. O script executa testes unitários, Android Lint, compilação do APK e verificação de assinatura.

Com Java e Android SDK previamente configurados, use também o Gradle Wrapper. É necessário ter `.tools/debug.keystore` ou ajustar a configuração de assinatura de testes.

O fluxo GitHub Actions compila e disponibiliza o APK de testes nos artefatos de cada execução.

## Validação

Os 20 testes automatizados cobrem contagens, pesos, total zero, nome do arquivo, identificadores com zeros à esquerda, CSV com aspas/quebras de linha, Excel, rejeição de FEIDs ausentes ou repetidos, unicidade FEID/dia, preservação de avaliações na atualização do catálogo, preservação dos usuários ao limpar coletas, histórico do coletor excluído, inicialização sem câmera autorizada e proporções em quatro tamanhos/orientações de tela. Banco e tela são verificados em ambiente Android simulado com Robolectric.

O leitor do aplicativo foi conferido com o modelo real de 7.804 amostras. O Excel de saída foi conferido independentemente com openpyxl, incluindo 20 colunas, identificadores, datas, fórmulas e valores em cache. Android Lint é executado sem supressão de erros. A leitura de câmera, permissões reais e a integração específica do OneDrive precisam de validação nos celulares/tablets que serão usados no campo.

Os modelos com dados de pesquisa não são incorporados ao APK nem publicados neste repositório. O coletor seleciona a entrada no próprio aparelho.
