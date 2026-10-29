import br.com.phs.coleta.Domain;
import br.com.phs.coleta.SheetIO;
import java.io.File;
import java.util.List;

/** Optional local compatibility check. Does not copy research data into the repository. */
class VerifyModel {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Informe o caminho do modelo de entrada.");
        File source=new File(args[0]);List<Domain.Sample> samples=SheetIO.read(source,source.getName().toLowerCase().endsWith(".csv"));
        System.out.println("Importação pelo leitor do aplicativo: "+samples.size()+" amostras válidas; FEIDs únicos e preenchidos.");
    }
}
