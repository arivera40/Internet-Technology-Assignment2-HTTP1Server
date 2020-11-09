import java.io.BufferedReader;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;



public class CGIHandler{
    private String script;
    private String parameters;

    public CGIHandler(String script, String parameters){
        this.script = script;
        this.parameters = parameters;
    }

    public String executeCGI(){
        try{
            Runtime run = Runtime.getRuntime();
            Process proc = run.exec("C:\\Users\\river\\Desktop\\project_root2\\cgi_bin\\helloworld.cgi");

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            String s = null;
            while((s = stdInput.readLine()) != null){
                System.out.println(s);
            }

            return s;
            // OutputStream stdOutput = proc.getOutputStream().write(parameters.getBytes(), 0, parameters.getBytes().length);

        }catch(IOException e){
            e.printStackTrace();
        }
        return "Error occurred";
    }

}