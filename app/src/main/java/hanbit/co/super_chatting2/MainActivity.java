package hanbit.co.super_chatting2;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.gun0912.tedpermission.PermissionListener;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

/*
알고리즘]
1. 서버에 접속
- 닉네임을 서버로 전달한다
- 환영 메세지를 수신 받는다

2. 메세지 전달
- 키보드로부터 입력을 받은 메세지를 서버로 전달한다

3. 메세지 수신
- 서버에서 메세지가 수신될 때 까지 대기한다
- 서버에서 메세지가 수신되면 출력한다
 */
public class MainActivity extends AppCompatActivity {

    // 서버 접속 여부를 판별하기 위한 변수
    boolean isConnect = false;

    // 뷰변수
    EditText edit1;
    Button btn1,btn_quit,gallerybtn;
    LinearLayout container;
    ScrollView scroll;
    ProgressDialog pro;


    // 어플 종료시 스레드 중지를 위해
    boolean isRunning = false;

    // 서버와 연결되어있는 소켓 객체
    Socket member_socket;

    // 사용자 닉네임(내 닉네임과 일챃면 내가 보낸 말풍선으로 설정, 아니면 반대 설정)
    String user_nickname;

    // room Name
    String room;

    // 갤러리
    int PICK_FROM_GALLERY = 123;

    // 비트맵
    Bitmap imagebitmap;

    // 이미지 절대경로
    String imageUriString;

    // PermissionListener 생성
    // 권한이 허가되거나 거부당했을 때 결과를 리턴해주는 리스너를 만들어 줌
    PermissionListener permissionListener = new PermissionListener() {
        @Override
        public void onPermissionGranted() {
            // 권한이 모두 허용 되고나서 실행된다
            Toast.makeText(MainActivity.this, "권한이 허용 됨", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPermissionDenied(ArrayList<String> deniedPermissios) {
            Toast.makeText(getApplicationContext(),"권한이 거부 됨",Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int SDK_INT = android.os.Build.VERSION.SDK_INT;

        if (SDK_INT > 8){
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        /*
        외부 저장소에 쓰레가 읽기를 시도 할 때 저장 매체 확인 가능한지 확인
         */
        checkPermission();

        // 뷰연결
        edit1 = findViewById(R.id.editText);
        btn1 = findViewById(R.id.button);
        container=findViewById(R.id.container);
        scroll=findViewById(R.id.scroll);
        btn_quit = findViewById(R.id.btn_quit);
        gallerybtn = findViewById(R.id.gallerybtn);



        // intent 받아오기
        room = getIntent().getStringExtra("room");
        Log.d("실행","roomName="+room);

        // 접속종료
        btn_quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SendToServerThread thread=new SendToServerThread(member_socket,"종료");
                thread.start();

            }
        });

        // 갤러리 버튼
        gallerybtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("실행","갤러리 버튼 누름");
                Intent intent = new Intent();
                intent.setType("image/*");
                // 이미지를 열 수 있는 앱을 호출
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent,PICK_FROM_GALLERY);
            }
        });



    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode==PICK_FROM_GALLERY
                && resultCode==RESULT_OK
                && data !=null
                && data.getData()!=null)
        {
            try {
                // 콘텐츠 제공자(Contetn Provider)에 접근하여, 필요한 데이터 얻어오기 +
                // 컨텐츠 URI와 연관된 컨텐츠에 대한 스트림을 연다
                InputStream in = getContentResolver().openInputStream(data.getData());

                // 비트맵
                imagebitmap = BitmapFactory.decodeStream(in);
                    /*
                    BitmapFactory = 여러가지 이미지 포맷을 decode해서
                    bitmap으로 변환 하는 함수드로 되어있다.

                    inputStream으로부터 Bitmap을 만들어 준다.
                     */

                // 파일 닫기
                try{in.close();}catch(IOException e){e.printStackTrace();}

                // 절대경로 받아오기
                GetUri getUri = new GetUri();
                imageUriString = getUri.getPath(getApplicationContext(),data.getData());
                Log.d("실행","imageUriString="+imageUriString);

                // 이미지를 서버에 전송하기
                FileSender fileSender = new FileSender(member_socket,imageUriString);
                fileSender.start();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    // 파일 전송용 스레드
    class FileSender extends Thread{
        String filePath;
        String fileNm;
        Socket socket;
        DataOutputStream dos;
        FileInputStream fis;
        BufferedInputStream bis;

        // 생성자
        public FileSender(Socket socket,String filePath){
            this.socket = socket; // 소켓 셋팅
            this.filePath = filePath;  // 파일의 절대 경로

            // 파일명명
            Random generator = new Random();
            int n = 1000000;
            n = generator.nextInt(n);
            fileNm = "Image-"+n+".jpg";

            try {
                // 데이터 전송용 스트림 생성
                dos = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                Log.d("실행","DataOutputStream에러-"+e.getMessage());
                e.printStackTrace();
            }finally {
                // 파일 받기 스레드 시작
//                File_FROM_SERVER ffs = new File_FROM_SERVER(member_socket);
//                ffs.start();
            }
        }

        // 실행코드
        @Override
        public void run() {

            try{
                // 파일 전송을 서버에 알린다
                dos.writeUTF("file");
                dos.flush();

                // 전송 할 파일을 읽어서 Socket Server에 전송
                String result = fileRead(dos);
                Log.d("실행","result:"+result);

            }catch(IOException e) {
                Log.d("실행","dos.writeUTF에러-"+e.getMessage());
                e.printStackTrace();
            }finally{ //리소스 초기화
                try {
                    fis.close();
                } catch (IOException e)
                {
                    Log.d("실행","bis.close()에러-"+e.getMessage());
                    e.printStackTrace();
                }
            }

        } // end run

        // 파일을 전송하는 함수
        private String fileRead(DataOutputStream dos){
            String result;

            try {
                dos.writeUTF(fileNm);
                Log.d("실행","파일 이름(" + fileNm + ")을 전송하였습니다.");

                // 파일을 읽어서 서버에 전송
                File file = new File(imageUriString);
                    /*
                    File(String pathname) = pathname에 해당되는 파일의
                    File객체를 생성한다.
                     */

                // 파일 사이즈
                Log.d("실행","file.size()="+file.length());
                // 파일 사이즈 보내기
                dos.writeUTF(file.length()+"");
                dos.flush();

                fis = new FileInputStream(file);
                    /*
                    FileInputStream = 바이트 단위로 입력 할 수 있도록
                    하기 위해서
                    -> 다른 입력 관련 클래스와 연결해서,
                    파일에서 데이터를 읽거나 쓸 수 있다.

                    FileInputStream(String name)
                    = 주어진 이름이 가리키는 파일을 바이트 스트림으로
                    읽기 위한 FileInputStream객체를 생성
                    -> 만약 FileInputStream객체의 생성자에 지정한
                    파일이 존재하지 않는 경우에는 FileNotFoundException
                    발생시킨다.
                     */
                bis = new BufferedInputStream(fis);
                    /*
                    FileInputStream은 1byte 단위로 입/출력이 이루어지기
                    때문에, 기계적인 동작이 많아지므로 효율이 떨어짐
                    => BufferedInputStream사용하면 편리하고 효율적인
                    입출력 가능

                    BufferedInputStream(바이트 입력 스트림)
                    */

                int len;
                int size= 4096;
                byte[] data = new byte[size];
                while((len=bis.read(data)) != -1){
                    dos.write(data,0,len);
                    dos.flush();
                }
                    /*
                    BufferedInputStream.read(byte[] b,int off,int len)
                    = len만큼을 읽어 byte[] b의 off위치에 저장하고
                    읽은 바이트 수를 반환한다
                     */
                // 서버에 전송(서버로 보내기 위해서 flush를 사용해야 한다)
                dos.flush();
                result = "SUCCESS";
            } catch (IOException e) {
                Log.d("실행","dos.writeUTF에러-"+e.getMessage());
                e.printStackTrace();
                result = "ERROR";
            }finally{
                try { bis.close(); } catch (IOException e) { e.printStackTrace(); }

            }

            return result;

        } // end fileRead
    }

    // 파일 받기 스레드
//    class File_FROM_SERVER extends Thread{
//        Socket socket;
//        DataInputStream dis;
//
//        public File_FROM_SERVER(Socket socket){
//            this.socket = socket;
//            try {
//                InputStream is
//                        = socket.getInputStream();
//                dis = new DataInputStream(is);
//            } catch (IOException e) {
//                e.printStackTrace();
//                Log.d("실행","InputStream에러-"+e.getMessage());
//            }
//        }
//
//        @Override
//        public void run() {
//            try{
//                // 서버로부터 (계속-while) 데이터를 수신받는다
//                while(isRunning){
//                    final String msg = dis.readUTF();
//                    Log.d("실행","msg="+msg);
//                }
//            }catch (Exception e){
//                e.printStackTrace();
//                Log.d("실행","InputStream에러-"+e.getMessage());
//            }
//        }
//    }

    private void checkPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // 마시멜로우 버전과 같거나 이상이라면
            if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "외부 저장소 사용을 위해 읽기/쓰기 필요", Toast.LENGTH_SHORT).show();
                }

                requestPermissions(new String[]
                                {Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE},
                        2);  //마지막 인자는 체크해야될 권한 갯수

            } else {
                Toast.makeText(this, "권한 승인되었음", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /*
        접속 버튼을 눌렀을 때 스레드를 가동시켜 프로그램 기능들을 구현
         */
    // 버튼과 연결된 메소드
    public void btnMethod(View v){
        if(isConnect==false){ // 접속전 -> 클라이언트 연결을 위한 코딩
            // 사용자가 입력한 닉네임을 받는다
            String nickName = edit1.getText().toString();


            if(nickName.length() >0 && nickName != null){ // nickName이 널이 아닌 경우에
                // 서버에 접속한다
                pro = ProgressDialog.show(this, null, "접속중입니다");

                // 접속 스레드 가동
                ConnectionThread thread = new ConnectionThread();
                thread.start();
            }else{ // 닉네임이 입력되지 않을 경우 다이얼로그창 띄운다
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("닉네임을 입력해주세요");
                builder.setPositiveButton("확인", null);
                builder.show();
            }
        }else{ // 접속후 -> 입력받은 메세지 문자열과 함께, SendToServerThread를
            // 가동하여 접속자의 메세지를 채팅방에 있는 다른 모든 사람에게 뿌려주는 기능
            // 입력 한 문자열을 가져온다
            String msg=edit1.getText().toString();

            // 송신 스레드 가동
            SendToServerThread thread=new SendToServerThread(member_socket,msg);
            thread.start();
        }

    }



    /*
    서버접속 처리하는 스레드 클래스
    (안드로이드에서 네트워크 관련 동작은 항상 메인 스레드가 아닌 스레드에서 처리)
    => 서버와 연결하고, 서버와 자신 클라이언트 사이에 스트림을 구성해 놓기
     */
    class ConnectionThread extends Thread{

        public void run(){
            try{
                // 접속한다
                final Socket socket
                        = new Socket("13.209.234.165",2345);
                member_socket = socket;

                // 미리 입력했던 닉네임을 서버로 전달한다
                String nickName
                        = edit1.getText().toString();
                user_nickname = nickName; // 화자에 따라 말풍선 바꿔주기 위해

                // 스트림을 추출
                    // 스트림으로 전달받은 닉네임을 서버에게 보내
                    // 클라이언트가 누구인지 클라이언트를 닉네임으로 등록
                    // 하도록 처리해줌
                OutputStream os  = socket.getOutputStream();
                    // 출력스트림을 socket.getOutputStream()으로 부터 얻어옴
                DataOutputStream dos = new DataOutputStream(os);
                    /*
                    DataOutputStream
                    - oustputStream을 인자로 DataOutputStream을 생성한다
                    = 그 하위 클래스들을 받아들인다는 의미도 포함되어 있음
                    - 자바의 기본형 데이터인 int, float, double, boolean, short, byte
                    등의 정보를 입력하고 출력하는 데 알맞는 클래스
                     */

                // 닉네임을 송신한다
                dos.writeUTF(nickName+"§"+room);
                dos.flush();
                    /*
                    DataOutput.writeUTF(String s)
                    = UTF-8인코딩을 사용해서 문자열을 출력한다
                    (네트워크 프로그래밍을 할 때, 문자열 전송시 자주 사용됨)
                     */

                // ProgressDialog를 제거한다
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pro.dismiss();
                        edit1.setText("");
                        edit1.setHint("메세지 입력");
                        btn1.setText("전송");

                        // 접속 상태를 true로 셋팅한다.
                        isConnect=true;
                        // 메세지 수신을 위한 스레드 가동
                        isRunning=true;

                        // 메세지를 받아들이는 쓰레드 시작
                        MessageThread thread
                                = new MessageThread(socket);
                        thread.start();
                    }
                });

            }catch (Exception e) {
                e.printStackTrace();
                Log.d("실행","소켓오류-"+e.getMessage());
                try {
                    member_socket.close();
                } catch (IOException ex) {
                    Log.d("실행","close오류-"+ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }
    }

    /*
    메세지 작업을 처리하는 스레드
    : 전달받은 메세지를 불러들여, 안드로이드 뷰 화면에 TextView를 새로 만들어
    계속 추가 해 주는 방법
     */
    class MessageThread extends Thread{
        Socket socket;
        DataInputStream dis;

        public MessageThread(Socket socket){
            this.socket = socket;
            try {
                InputStream is
                        = socket.getInputStream();
                dis = new DataInputStream(is);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("실행","InputStream에러-"+e.getMessage());
            }
        }

        // 실행 할 코드

        @Override
        public void run() {
            try{
                // 메세지가 들어올 때마다 실행되어야 하므로
                // while무한루프르 통해 계속 대기상태
                while(isRunning){
                    // 서버로부터 (계속-while)데이터를 수신받는다
                    final String msg
                            =dis.readUTF();
                    // 서버로 부터 받은 메세지 출력
                    Log.d("실행","서버로 부터 받은 메세지="+msg);


                    // 화면에 출력
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 텍스트뷰의 객체를 생성
                            TextView tv
                                    = new TextView(MainActivity.this);
                            tv.setTextColor(Color.BLACK);
                            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP,22);

                            // 메시지의 시작 이름이 내 이름과 일치하다면
//                            if(msg.startsWith(user_nickname)){
//                                tv.setBackgroundResource(R.drawable.me);
//                            }else{
//                                tv.setBackgroundResource(R.drawable.you);
//                            }
                            tv.setText(msg);
                            container.addView(tv);

                            // 제일 하단으로 스크롤 한다
                            scroll.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    // 서버에 데이터를 전달하는 스레드
    /*
    MessageThread가 서버로부터 메세지를 계속 전달받아 TextView를 생성하여
    완전한 채팅프로그램
     */
    class SendToServerThread extends Thread{
        Socket socket;
        String msg;
        DataOutputStream dos;

        // 생성자
        public SendToServerThread(Socket socket,String msg){
            try{
                this.socket=socket;
                this.msg=msg;
                OutputStream os=socket.getOutputStream();
                dos=new DataOutputStream(os);
            }catch (Exception e){

            }
        }

        // 실행코드
        @Override
        public void run() {
            try{
                // 서버로 데이터를 보낸다
                // 전달받은 메세지를 dos.writeUTF(msg)통해 서버로
                // 데이터를 보내주기만 하면 된다
                dos.writeUTF(msg);
                dos.flush();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        edit1.setText("");
                    }
                });

                if(msg.equals("종료")){
                    member_socket.close();
                    isRunning=false;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /*
    어플리케이션 종료시 백그라운드에서 계속 실행 될 수 있는
    스렏를 종료함
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            member_socket.close();
            isRunning=false;

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
