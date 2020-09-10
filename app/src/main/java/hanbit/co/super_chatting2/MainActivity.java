package hanbit.co.super_chatting2;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
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
    Button btn1,btn_quit;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int SDK_INT = android.os.Build.VERSION.SDK_INT;

        if (SDK_INT > 8){
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        // 뷰연결
        edit1 = findViewById(R.id.editText);
        btn1 = findViewById(R.id.button);
        container=findViewById(R.id.container);
        scroll=findViewById(R.id.scroll);
        btn_quit = findViewById(R.id.btn_quit);

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


    }

    /*
    접속 버튼을 눌렀을 때 스레드를 가동시켜 프로그램 기능들을 구현
     */
    // 버튼과 연결된 메소드
    public void btnMethod(View v){
        if(isConnect==false){ // 접속전 -> 클라이언트 연결을 위한 코딩
            // 사용자가 입력한 닉네임을 받는다
            String nickName = edit1.getText().toString();
            if(nickName.length() >0 && nickName != null){
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
                        = new Socket("13.209.234.165",1234);
                member_socket = socket;

                // 미리 입력했던 닉네임을 서버로 전달한다
                String nickName
                        = edit1.getText().toString();
                user_nickname = nickName; // 화자에 따라 말품선 마꿔주기 위해

                // 스트림을 추출
                    // 스트림으로 전달받은 닉네임을 서버에게 보내
                    // 클라이언트가 누구인지 클라이언트를 닉네임으로 등록
                    // 하도록 처리해줌
                OutputStream os  = socket.getOutputStream();
                    // 출력스트림을 socket.getOutputStream()으로 부터 얻어옴
                DataOutputStream dos = new DataOutputStream(os);
                    /*
                    DataOutputStream
                    =
                     */

                // 닉네임을 송신한다
                dos.writeUTF(nickName+"§"+room);

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

                        MessageThread thread
                                = new MessageThread(socket);
                        thread.start();
                    }
                });

            }catch (Exception e) {
                e.printStackTrace();
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
                // 데잍러ㅡㄹ 보내쥐만 하면 도니다
                dos.writeUTF(msg);
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
