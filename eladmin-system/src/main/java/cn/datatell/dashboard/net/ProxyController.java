package cn.datatell.dashboard.net;

//import cn.datatell.client.HaystackServiceManager;
import org.apache.commons.io.CopyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.projecthaystack.client.HClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;

@Controller
@RequestMapping(value = {"/ui","/ui2","/api/hklcmc","/pod","/brand"})
public class ProxyController {
    @Value("${proxy_host}")
    private String proxyHost;
    @Value("${spring.resources.staticLocations}")
    private String  cachePath;
    public static HClient hClient;
    private static final Log LOG = LogFactory.getLog(ProxyController.class);

    @RequestMapping(value = "**")
    @ResponseBody
    public Object proxy(HttpServletRequest request, HttpServletResponse response) {
        String rst="not yet";
        try {
            CopyUtils cu = new CopyUtils();
            boolean cacheflag=cacheable(request.getServletPath());

            // System.out.println(request.getServletPath());
            if (cacheflag&&request.getMethod().equals("GET")) {
                // System.out.println(request.getRequestURL());
                //System.out.println(request.getPathInfo());
                LOG.debug(cachePath.substring(5)+"/cache" + request.getServletPath());
                File tfile = new File(cachePath.substring(5)+"/cache" + request.getServletPath());
                if (tfile.exists()) {
//            LOG.info("use cache for:"+request.getServletPath());
                    FileInputStream fis=new FileInputStream(tfile);
                    response.setDateHeader("Expires", System.currentTimeMillis() + 1000 * 3600*240);
                    response.setHeader("Cache-Control","max-age=108000");
                    OutputStream outStream = response.getOutputStream();
                    cu.copy(fis, outStream);
                    if(request.getServletPath().endsWith(".css")){
                        response.setContentType("text/css; charset=utf-8");

                    }else if(request.getServletPath().endsWith(".js")||request.getServletPath().endsWith(".js.map")){
                        response.setContentType("text/javascript; charset=utf-8");
                    }
                    outStream.flush();
                    return "";
                    //   response.set
                }
                //  response.sendRedirect( "/cache"+request.getServletPath());
            }

            HClient hClient =null;
                    //= HaystackServiceManager.getHaystackService("cmc").getCloneClient();
//    hClient.open();
            String url = proxyHost + request.getServletPath();
            if (request.getQueryString() != null && request.getQueryString().length() > 0)
                url = url + "?" + request.getQueryString();
            LOG.debug(url);
            //   System.out.println(request.getContentType());
            //   System.out.println(request.getHeaders("Content-Type"));
            HttpURLConnection resp;
            if (request.getMethod().equals("POST")&&request.getHeader("SkyArc-UI-Session-Key")!=null) {
                HashMap<String,String> hds=new HashMap();
                hds.put("SkyArc-UI-Session-Key",request.getHeader("SkyArc-UI-Session-Key"));
                resp = hClient.any(url, request.getMethod(), request.getContentType(),hds);

            }else {
                resp = hClient.any(url, request.getMethod(), request.getContentType());

            }
            if(cacheflag){
                File cfile=new File(cachePath.substring(5) +"/cache"+ request.getServletPath());
                if(isExist(cfile.getAbsolutePath())){
                    FileOutputStream fos = new FileOutputStream(cfile);
                    cu.copy(resp.getInputStream(), fos);
                    fos.flush();
                    resp.disconnect();
                    fos.close();
                    LOG.info("Write File"+request.getServletPath());
                    response.sendRedirect( "/cache"+request.getServletPath());
                    return rst;
                }

            }
            if (request.getMethod().equals("POST")) {
                //  Writer cout = new OutputStreamWriter(resp.getOutputStream(), "UTF-8");
                //  cout.write(request.getInputStream())
                cu.copy(request.getInputStream(), resp.getOutputStream());
            }
            int code = resp.getResponseCode();
            if (code == 403) {
//                hClient = HaystackServiceManager.getHaystackService("cmc").getClient().open();
                resp = hClient.any(url, request.getMethod(), request.getContentType());
            }else if(code == 302){
                response.sendRedirect( resp.getHeaderField("Location"));
            }else if(code>200){
                response.setStatus(code);
            }
            response.setContentType(resp.getContentType());


            for (String key : resp.getHeaderFields().keySet()) {
                if (key == null || key.startsWith("Connection")) {
                    continue;
                }

                response.addHeader(key, resp.getHeaderField(key));
            }
            String  ccookis= (String) hClient.getAuth().headers.get("Cookie");
            String  rcookies=resp.getRequestProperty("Cookie");
            if(ccookis!=null&&rcookies!=null&&rcookies.indexOf("skyarc-auth-")<0){
                rcookies=ccookis+";"+rcookies;
            }
            response.setHeader("Cookie", rcookies);
            OutputStream outStream = response.getOutputStream();
            cu.copy(resp.getInputStream(), outStream);
            outStream.flush();
            resp.disconnect();
            outStream.close();
        }catch(Exception e){
            e.printStackTrace();
//    e.getMessage();
        }
        return rst;

    }
    public boolean  cacheable(String path){
        if(path!=null){
            String[]  eds={".js",".css",".js.map",".ttf"};
            for(String ed:eds){
                if(path.endsWith(ed)){
                    return true;
                }
            }

        }
        return false;
    }
    public static boolean isWindows(){
        boolean ret=false;
        String os = System.getProperty("os.name");
        if(os.toLowerCase().startsWith("win")){
            ret=true;
        }
        return ret;
    }
    public static boolean isExist(String filePath) {
        String reg="/";
        if(isWindows()){
            reg="\\\\";
        }
        String paths[] = filePath.split(reg);
        String dir = paths[0];
        for (int i = 0; i < paths.length - 2; i++) {//注意此处循环的长度
            try {
                dir = dir + "/" + paths[i + 1];
                File dirFile = new File(dir);
                if (!dirFile.exists()) {
                    dirFile.mkdir();
                    System.out.println("创建目录为：" + dir);
                }
            } catch (Exception err) {
                System.err.println("文件夹创建发生异常");
            }
        }
        File fp = new File(filePath);
        if(!fp.exists()){
            return true; // 文件不存在，执行下载功能
        }else{
            return false; // 文件存在不做处理
        }
    }
}