# PHS Reader — versão 0.4.0

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

## Exportação na memória interna

Escolha o dia e o formato, toque em **Exportar** e selecione ou crie uma pasta na memória interna, por exemplo **Documentos/PHS**. A escolha é feita a cada exportação e não aparece nas configurações. O seletor solicita somente destinos locais; o aplicativo também confere o resultado para aceitar apenas pastas do armazenamento interno, rejeitando nuvem e cartão SD.

O Excel/CSV fica acessível pelo aplicativo Arquivos do Android na pasta escolhida. Se o arquivo já existir, há confirmação antes de substituir. As avaliações permanecem no banco local, inclusive após exportar ou cancelar a escolha de pasta. Em Android 11 ou superior, algumas raízes de armazenamento são bloqueadas pelo sistema: use uma subpasta própria, como PHS.

Não há login Microsoft, envio ao OneDrive ou registro de aplicativo nesta versão. A atualização remove as configurações antigas de nuvem, mantendo usuários, nome do aparelho, catálogo de entrada e avaliações. O APK não solicita permissão de Internet. A seleção do arquivo de entrada continua pelo seletor de documentos Android; um provedor instalado pode fornecer esse arquivo sem integração própria do PHS Reader.


## Instalar

[Baixar PHS Reader 0.4.0 no GitHub](https://github.com/WheatResearchGDM/PHS/releases/download/v0.4.0/PHS-Reader-0.4.0.apk). O APK está em **Releases**, disponível independentemente dos artefatos temporários de Actions.

O instalador gerado em `dist/PHS-Reader-0.4.0.apk` mantém o pacote e a chave local de testes da versão 0.1.0 publicada em Releases. Instale por cima para preservar as coletas, sem desinstalar. Compatível com Android 8.0/API 26 ou superior, câmera traseira opcional. Conceda a permissão de câmera para ler QR Codes.

Para manter atualizações locais compatíveis, preserve `.tools/debug.keystore`. Ela não é enviada ao GitHub. Os APKs produzidos pelo CI usam uma chave de testes temporária e não substituem uma instalação assinada com outra chave. A versão de produção deve adotar uma chave de distribuição estável e protegida.

## Compilar no Windows

```powershell
./tools/build.ps1 -Prepare
```

Java 17, Gradle 8.11.1 e SDK Android 35 são preparados em `.tools`, dentro deste projeto. `local.properties`, ferramentas, caches, chaves e artefatos binários não são versionados. O script executa testes unitários, Android Lint, compilação do APK e verificação de assinatura.

Com Java e Android SDK previamente configurados, use também o Gradle Wrapper. É necessário ter `.tools/debug.keystore` ou ajustar a configuração de assinatura de testes.

O fluxo GitHub Actions compila e disponibiliza o APK de testes nos artefatos de cada execução.

## Validação

Os testes automatizados cobrem coleta, persistência, contagens, troca de dia, layout, CSV/Excel, escolha da pasta local, rejeição de nuvem e volumes removíveis, preservação das coletas e remoção das configurações antigas de nuvem. Banco e interface são verificados em ambiente Android simulado com Robolectric.

O leitor do aplicativo foi conferido com o modelo real de 7.804 amostras. O Excel de saída foi conferido independentemente com openpyxl, incluindo 20 colunas, identificadores, datas, fórmulas e valores em cache. Android Lint é executado sem supressão de erros. A câmera e o seletor de pastas ainda precisam de validação nos celulares/tablets usados no campo.

Os modelos com dados de pesquisa não são incorporados ao APK nem publicados neste repositório. O coletor seleciona a entrada no próprio aparelho.
