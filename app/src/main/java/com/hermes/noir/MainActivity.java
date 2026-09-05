package com.hermes.noir;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff000000, CARD=0xff141414, LINE=0xff262626,
        WHITE=0xfff5f5f5, MUTED=0xff828282, ACCENT=0xffffffff, SECONDARY=0xffb0b0b0;
    private Store store;
    private LinearLayout root, body, messageList;
    private ScrollView chatScroll;
    private EditText composer;
    private TextView attachmentLabel, sendButton;
    private String page="bots", currentThread="", visibleThread="", attached="", draft="", exportText="";
    private boolean arabic=false;
    private BotWizard openWizard;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long revision=-1;
    private interface Task { void go() throws Exception; }
    private final Runnable poll=new Runnable(){public void run(){
        if(ChatService.revision!=revision){revision=ChatService.revision;if(page.equals("chat"))act(()->renderMessages());}
        handler.postDelayed(this,250);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        try {
            store=Store.get(this);arabic=store.read().optString("language","en").equals("ar");
            if(state!=null){page=state.getString("page","bots");currentThread=state.getString("thread","");draft=state.getString("draft","");}
            String launch=getIntent().getStringExtra("thread");if(launch!=null){currentThread=launch;page="chat";}
            show();
        } catch(Exception e){
            TextView error=label("Could not open encrypted app storage. Your saved data has not been replaced.",18,WHITE);
            error.setPadding(dp(28),dp(80),dp(28),dp(28));error.setBackgroundColor(BG);setContentView(error);
        }
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);String id=i.getStringExtra("thread");if(id!=null){currentThread=id;page="chat";act(()->show());}}
    @Override protected void onResume(){super.onResume();handler.post(poll);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(poll);markRead();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("page",page);b.putString("thread",currentThread);b.putString("draft",composer==null?draft:composer.getText().toString());}
    @Override public void onBackPressed(){if(page.equals("chat")){draft="";attached="";page="bots";act(()->show());}else super.onBackPressed();}
    private String tr(String ar,String en){return arabic?ar:en;}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void act(Task task){try{task.go();}catch(Exception e){alert(tr("تعذّر تنفيذ العملية","Could not complete action"),e.getMessage()==null?"Please try again.":e.getMessage());}}
    private void alert(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(tr("تمام","OK"),null).show();}
    private TextView label(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable shape(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView button(String title,boolean primary,Task action){
        TextView b=label(title,14,primary?BG:WHITE);b.setTypeface(null,Typeface.BOLD);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(17),dp(13),dp(17),dp(13));b.setMinHeight(dp(44));b.setBackground(shape(primary?ACCENT:BG,primary?0:LINE,28));
        b.setOnClickListener(v->act(action));return b;
    }
    private void gap(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private void margin(View v,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(bottom);v.setLayoutParams(p);}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(shape(CARD,LINE,22));margin(c,12);return c;}
    private void header(String title,String subtitle){
        LinearLayout h=row();h.setPadding(dp(20),dp(18),dp(14),dp(8));
        LinearLayout words=column();TextView big=label(title,27,WHITE);big.setTypeface(null,Typeface.BOLD);words.addView(big);gap(words,5);
        if(!subtitle.isEmpty())words.addView(label(subtitle,12,MUTED));
        h.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=label("☷",24,MUTED);settings.setGravity(Gravity.CENTER);settings.setContentDescription(tr("الإعدادات","Settings"));
        settings.setOnClickListener(v->{page="settings";act(()->show());});h.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(h);
    }

    private void show() throws Exception {
        markRead();
        composer=null;messageList=null;
        root=column();root.setBackgroundColor(BG);root.setLayoutDirection(arabic?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });setContentView(root);root.requestApplyInsets();
        if(page.equals("chat")){showChat();return;}
        if(page.equals("settings")){
            LinearLayout top=row();top.setPadding(dp(12),dp(12),dp(20),dp(8));top.addView(back(()->{page="bots";show();}));top.addView(label(tr("الإعدادات","Settings"),24,WHITE));root.addView(top);
        }else header(tr("البوتات","Bots"),"○  "+store.read().optString("serverName","birella"));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(dp(20),dp(8),dp(20),dp(12));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(page.equals("settings")){showSettings();return;}
        showBots();
        LinearLayout footer=row();footer.setGravity(Gravity.CENTER);footer.setPadding(dp(20),dp(12),dp(20),dp(18));
        footer.addView(button(tr("جروب جديد","New group"),false,()->newGroup()));
        TextView add=button(tr("بوت جديد","New Bot"),true,()->editBot(null));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginStart(dp(12));footer.addView(add,lp);root.addView(footer);
    }

    private JSONObject bot(String id)throws Exception{return Store.find(store.read().getJSONArray("bots"),id);}
    private JSONObject thread()throws Exception{return Store.find(store.read().getJSONArray("threads"),currentThread);}
    private void showChats()throws Exception {showBots();}

    private void showBots()throws Exception {
        JSONObject data=store.read();JSONArray bots=data.getJSONArray("bots"),threads=data.getJSONArray("threads");
        ArrayList<JSONObject> entries=new ArrayList<>();
        for(int i=0;i<bots.length();i++){
            JSONObject b=bots.getJSONObject(i),latest=null;
            for(int j=0;j<threads.length();j++){JSONObject t=threads.getJSONObject(j);if(!Conversations.group(t)&&t.optString("botId").equals(b.getString("id"))&&(latest==null||t.optLong("updated")>latest.optLong("updated")))latest=t;}
            entries.add(new JSONObject().put("bot",b).put("thread",latest==null?JSONObject.NULL:latest).put("updated",latest==null?0:latest.optLong("updated")));
        }
        for(int i=0;i<threads.length();i++)if(Conversations.group(threads.getJSONObject(i)))entries.add(new JSONObject().put("thread",threads.getJSONObject(i)).put("updated",threads.getJSONObject(i).optLong("updated")));
        entries.sort((a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));
        if(entries.isEmpty()){
            gap(body,60);TextView empty=label(tr("بوتاتك هتظهر هنا","Your bots will appear here"),18,WHITE);empty.setGravity(Gravity.CENTER);body.addView(empty);gap(body,10);
            TextView hint=label(tr("وصّل أول بوت عشان تبدأ.","Connect your first bot to get started."),14,MUTED);hint.setGravity(Gravity.CENTER);body.addView(hint);
        }
        for(JSONObject entry:entries){
            JSONObject b=entry.optJSONObject("bot"),t=entry.optJSONObject("thread");boolean group=b==null;
            LinearLayout line=row();line.setPadding(0,dp(13),0,dp(13));
            line.addView(avatar(group?null:b,44),new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout words=column();words.setPadding(dp(12),0,dp(8),0);
            TextView title=label(group?t.getString("title"):b.getString("name"),16,WHITE);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);words.addView(title);gap(words,4);
            JSONArray messages=t==null?new JSONArray():t.getJSONArray("messages");String preview=messages.length()==0?(group?tr("جروب جديد","New group"):b.optString("description",tr("لا توجد رسائل بعد","No messages yet"))):plain(messages.getJSONObject(messages.length()-1).opt("content"));
            String state=b==null?"":b.optString("connectionStatus","");
            String stateLabel=state.equals("online")?tr("متصل","Online"):state.equals("offline")?tr("غير متصل","Offline"):"";
            String shown=preview.isEmpty()?tr("لا توجد رسائل بعد","No messages yet"):preview;
            if(!stateLabel.isEmpty())shown += "  ·  " + stateLabel;
            TextView subtitle=label(shown,13,state.equals("offline")?0xffed7171:MUTED);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);words.addView(subtitle);line.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            if(t!=null){
                LinearLayout meta=column();meta.setGravity(Gravity.END);meta.addView(label(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(t.optLong("updated"))),10,MUTED));
                if(t.optLong("updated")>t.optLong("readAt")&&messages.length()>0&&messages.getJSONObject(messages.length()-1).optString("role").equals("assistant")){gap(meta,6);meta.addView(label("●",10,WHITE));}line.addView(meta);
            }
            line.setOnClickListener(v->act(()->{if(t==null)newThread(b.getString("id"));else{currentThread=t.getString("id");page="chat";draft="";attached="";show();}}));
            line.setOnLongClickListener(v->{act(()->{if(b!=null)editBot(b);else{currentThread=t.getString("id");page="chat";show();chatMenu();}});return true;});body.addView(line);
        }
    }
    private View avatar(JSONObject bot,int size){
        String photo=bot==null?"":bot.optString("photo", "");
        if(!photo.isEmpty())try{
            byte[] bytes=android.util.Base64.decode(photo,android.util.Base64.DEFAULT);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            if(bitmap!=null){ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(shape(CARD,0,12));image.setClipToOutline(true);return image;}
        }catch(Exception ignored){}
        TextView a=label(bot==null?"◉":bot.optString("avatar","🤖"),size*.56f,WHITE);a.setGravity(Gravity.CENTER);a.setBackground(shape(CARD,0,12));return a;
    }
    private TextView back(Task task){TextView b=label(arabic?"›":"‹",32,WHITE);b.setGravity(Gravity.CENTER);b.setContentDescription(tr("رجوع","Back"));b.setOnClickListener(v->act(task));b.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));return b;}
    private void newGroup()throws Exception {
        JSONArray bots=store.read().getJSONArray("bots");if(bots.length()<2){alert(tr("جروب جديد","New group"),tr("أضف بوتين على الأقل الأول.","Connect at least two bots first."));return;}
        LinearLayout form=column();form.setPadding(dp(22),dp(12),dp(22),dp(12));EditText title=field(form,tr("اسم الجروب","Group name"),"",false);
        ArrayList<CheckBox> boxes=new ArrayList<>();
        for(int i=0;i<bots.length();i++){JSONObject b=bots.getJSONObject(i);CheckBox box=new CheckBox(this);box.setText(b.optString("avatar","🤖")+"  "+b.getString("name")+"  @"+Conversations.handle(b));box.setTextColor(WHITE);form.addView(box);boxes.add(box);}
        gap(form,14);form.addView(label(tr("الرسالة بدون @mention تتبعت لكل الأعضاء بالترتيب. كل عضو هيشوف سياق الجروب.","Without an @mention, every member replies in order. Group conversation context is shared with each recipient."),13,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(tr("جروب جديد","New group")).setView(scroll).setNegativeButton(tr("رجوع","Cancel"),null).setPositiveButton(tr("إنشاء","Create"),null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->act(()->{
            JSONArray ids=new JSONArray();for(int i=0;i<boxes.size();i++)if(boxes.get(i).isChecked())ids.put(bots.getJSONObject(i).getString("id"));
            String name=title.getText().toString().trim();if(name.isEmpty())throw new Exception(tr("اكتب اسم الجروب.","Enter a group name."));
            currentThread=store.createGroup(ids,name);page="chat";draft="";attached="";dialog.dismiss();show();
        })));dialog.show();
    }

    private void chooseBot()throws Exception {
        JSONArray bots=store.read().getJSONArray("bots");if(bots.length()==0){editBot(null);return;}
        String[] names=new String[bots.length()];for(int i=0;i<names.length;i++)names[i]=bots.getJSONObject(i).getString("name");
        new AlertDialog.Builder(this).setTitle(tr("اختار البوت","Choose agent")).setItems(names,(d,n)->act(()->newThread(bots.getJSONObject(n).getString("id")))).show();
    }
    private void newThread(String botId)throws Exception {currentThread=store.createThread(botId,tr("محادثة جديدة","New conversation"));page="chat";draft="";attached="";show();}
    private EditText field(LinearLayout parent,String title,String value,boolean secret){
        parent.addView(label(title,12,MUTED));gap(parent,6);EditText e=new EditText(this);e.setTextSize(15);e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setSingleLine(true);e.setText(value);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(shape(BG,LINE,12));
        if(secret)e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        parent.addView(e);gap(parent,14);return e;
    }
    private void editBot(JSONObject original)throws Exception {
        if(ChatService.isActive()){alert(tr("فيه رد شغال","Reply in progress"),tr("استنى الرد يخلص الأول.","Wait for the current reply."));return;}
        openWizard=new BotWizard(original);openWizard.show();
    }

    private void showSettings()throws Exception {
        body.addView(button(tr("اتصال السيرفر الافتراضي","Default server connection"),false,()->editServer()));gap(body,14);
        body.addView(button(arabic?"Language: English":"اللغة: العربية",false,()->{arabic=!arabic;store.edit(d->d.put("language",arabic?"ar":"en"));show();}));gap(body,14);
        body.addView(button(tr("إشعارات الردود","Reply notifications"),false,()->{
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9);
            else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));
        }));gap(body,24);
        body.addView(button(tr("تصدير إعدادات البوتات","Export bot settings"),false,()->exportBotSettings()));gap(body,14);
        body.addView(button(tr("استيراد إعدادات البوتات","Import bot settings"),false,()->importBotSettings()));gap(body,24);
        body.addView(label(tr("الضغط المطوّل على البوت يفتح إعداداته.\n\nالمحادثات والمفاتيح مشفّرة على الجهاز. سجل Telegram مستقل. الإشعارات للردود التي تبدأها هنا فقط.\n\nإعداد الشخصية يخص محادثات التطبيق؛ لا يغيّر SOUL.md على السيرفر.","Long-press a bot to edit its identity and connection.\n\nChats and keys are encrypted on this device. Telegram history is separate. Notifications cover replies started here.\n\nPersonality applies to app requests; it does not modify server SOUL.md."),14,MUTED));gap(body,32);body.addView(label("Hermes · Android client 0.3.0",12,MUTED));
    }
    private void exportBotSettings(){
        act(()->{JSONObject out=new JSONObject().put("format","hermes-android-bots-v1").put("bots",store.read().getJSONArray("bots"));
            exportText=out.toString(2);startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"hermes-bots.json"),44);});
    }
    private void importBotSettings(){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),45);}
    private void importBotSettings(Uri uri){new Thread(()->{try{
        byte[] bytes;try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IOException("Cannot read settings file");byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>2*1024*1024)throw new Exception("Settings file is too large");}bytes=out.toByteArray();
        }
        JSONObject imported=new JSONObject(new String(bytes,StandardCharsets.UTF_8));JSONArray incoming=imported.getJSONArray("bots");
        store.edit(d->{JSONArray bots=d.getJSONArray("bots");for(int i=0;i<incoming.length();i++){JSONObject b=new JSONObject(incoming.getJSONObject(i).toString());
            if(!b.has("id"))b.put("id",Store.id());b.put("url",Endpoint.normalize(b.optString("url")));if(b.optString("key").isEmpty())throw new Exception("A bot is missing its API key");
            String handle=b.optString("handle");if(!handle.isEmpty())b.put("handle",MentionRouter.handle(handle));
            boolean replaced=false;for(int j=0;j<bots.length();j++)if(bots.getJSONObject(j).getString("id").equals(b.getString("id"))){bots.put(j,b);replaced=true;break;}if(!replaced)bots.put(b);
        }});
        runOnUiThread(()->act(()->{show();Toast.makeText(this,tr("تم استيراد الإعدادات","Bot settings imported"),Toast.LENGTH_SHORT).show();}));
    }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())alert(tr("فشل الاستيراد","Import failed"),e.getMessage());});}}).start();}
    private void editServer()throws Exception {
        JSONObject current=store.read().optJSONObject("server");LinearLayout form=column();form.setPadding(dp(22),dp(16),dp(22),dp(16));
        EditText name=field(form,tr("اسم السيرفر","Server name"),store.read().optString("serverName","birella"),false);
        EditText url=field(form,"HTTPS URL",current==null?"":current.optString("url"),false);url.setHint("https://birella.your-tailnet.ts.net");
        EditText key=field(form,"Hermes API key",current==null?"":current.optString("key"),true);
        form.addView(label(tr("يُستخدم للبوتات التي تضيفها بعد الحفظ. لا يغير اتصالات البوتات الموجودة.","Used as the default for newly added bots. Existing bot connections stay as configured."),13,MUTED));
        AlertDialog d=new AlertDialog.Builder(this).setTitle(tr("اتصال السيرفر","Server connection")).setView(form).setNegativeButton(tr("رجوع","Cancel"),null).setPositiveButton(tr("حفظ","Save"),null).setNeutralButton(tr("اختبار","Test"),null).create();
        java.util.concurrent.Callable<JSONObject> value=()->{String k=key.getText().toString().trim();if(k.isEmpty()||k.contains("\n")||k.contains("\r"))throw new Exception("Enter the Hermes API key");return new JSONObject().put("url",Endpoint.normalize(url.getText().toString())).put("key",k);};
        d.setOnShowListener(v->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->act(()->{JSONObject server=value.call();store.edit(x->x.put("server",server).put("serverName",name.getText().toString().trim()));d.dismiss();show();}));
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->act(()->testConnection(value.call(),d.getButton(AlertDialog.BUTTON_NEUTRAL))));});d.show();
    }
    private void testConnection(JSONObject bot,View button){
        button.setEnabled(false);new Thread(()->{String error=null;try{HermesApi.check(bot);}catch(Exception e){error=e.getMessage();}final String result=error;
            try { final String id=bot.optString("id"); if(!id.isEmpty()) store.edit(d->Store.find(d.getJSONArray("bots"),id).put("connectionStatus",result==null?"online":"offline")); } catch(Exception ignored){}
            runOnUiThread(()->{if(isDestroyed())return;button.setEnabled(true);alert(result==null?tr("الاتصال نجح","Connected"):tr("الاتصال لم ينجح","Connection failed"),result==null?tr("Hermes API رد بنجاح.","Hermes API responded successfully."):result);});}).start();
    }

    private void showChat()throws Exception {
        visibleThread=currentThread;
        JSONObject t=thread();boolean group=Conversations.group(t);JSONObject b=group?null:bot(t.getString("botId"));
        LinearLayout h=row();h.setPadding(dp(8),dp(8),dp(12),dp(8));h.addView(back(()->{page="bots";draft="";attached="";show();}));
        h.addView(avatar(b,32),new LinearLayout.LayoutParams(dp(32),dp(32)));
        LinearLayout names=column();names.setPadding(dp(10),0,dp(10),0);TextView title=label(group?t.getString("title"):b.getString("name"),16,WHITE);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);names.addView(title);
        String sub=group?Conversations.members(t).length()+tr(" أعضاء"," members"):(b.optString("model","hermes-agent").equals("hermes-agent")?tr("موديل Hermes الافتراضي","Hermes default"):b.optString("model"));names.addView(label(sub,11,MUTED));
        names.setOnClickListener(v->act(()->{if(group)showMembers();else editBot(b);}));h.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        TextView menu=label("⋯",22,MUTED);menu.setGravity(Gravity.CENTER);menu.setOnClickListener(v->act(()->chatMenu()));h.addView(menu,new LinearLayout.LayoutParams(dp(36),dp(44)));root.addView(h);
        chatScroll=new ScrollView(this);chatScroll.setFillViewport(true);messageList=column();messageList.setGravity(Gravity.BOTTOM);messageList.setPadding(dp(20),dp(18),dp(20),dp(14));chatScroll.addView(messageList);root.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout input=column();input.setPadding(dp(16),dp(6),dp(16),dp(12));attachmentLabel=label("",12,MUTED);attachmentLabel.setPadding(dp(8),dp(3),dp(8),dp(6));attachmentLabel.setOnClickListener(v->{attached="";updateAttachment();});input.addView(attachmentLabel);
        LinearLayout bar=row();bar.setPadding(dp(4),dp(3),dp(4),dp(3));bar.setBackground(shape(0xff0c0c0c,LINE,28));
        TextView add=label("+",23,MUTED);add.setGravity(Gravity.CENTER);add.setOnClickListener(v->pickImage());add.setContentDescription(tr("إرفاق صورة","Attach image"));bar.addView(add,new LinearLayout.LayoutParams(dp(38),dp(44)));
        composer=new EditText(this);composer.setTextColor(WHITE);composer.setHintTextColor(MUTED);composer.setTextSize(15);composer.setBackgroundColor(Color.TRANSPARENT);composer.setMaxLines(5);composer.setMinLines(1);
        composer.setHint(group?tr("رسالة أو @mention…","Message or @mention…"):tr("رسالة إلى ","Message ")+b.getString("name"));composer.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);composer.setText(draft);composer.setPadding(dp(3),dp(6),dp(5),dp(6));bar.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        sendButton=label("↑",24,BG);sendButton.setGravity(Gravity.CENTER);sendButton.setBackground(shape(WHITE,0,24));sendButton.setContentDescription(tr("إرسال","Send"));sendButton.setOnClickListener(v->act(()->send(false)));LinearLayout.LayoutParams send=new LinearLayout.LayoutParams(dp(34),dp(34));send.setMargins(dp(5),0,dp(4),0);bar.addView(sendButton,send);input.addView(bar);root.addView(input);updateAttachment();renderMessages();
    }
    private void showMembers()throws Exception {
        JSONArray ids=Conversations.members(thread());String[] names=new String[ids.length()];for(int i=0;i<ids.length();i++){JSONObject b=bot(ids.getString(i));names[i]=b.optString("avatar","🤖")+" "+b.getString("name")+"  @"+Conversations.handle(b);}
        new AlertDialog.Builder(this).setTitle(tr("الأعضاء — اضغط للإشارة","Members — tap to mention")).setItems(names,(d,n)->act(()->{JSONObject b=bot(ids.getString(n));composer.append("@"+Conversations.handle(b)+" ");composer.requestFocus();})).setNegativeButton(tr("رجوع","Close"),null).show();
    }

    private void markRead(){
        if(store==null||messageList==null||visibleThread.isEmpty())return;
        final String id=visibleThread;try{store.edit(d->{JSONObject t=Store.find(d.getJSONArray("threads"),id);t.put("readAt",System.currentTimeMillis());});}catch(Exception ignored){}
    }
    private void updateAttachment(){if(attachmentLabel!=null){attachmentLabel.setVisibility(attached.isEmpty()?View.GONE:View.VISIBLE);attachmentLabel.setText(tr("صورة مرفقة × اضغط لإزالتها","Image attached × Tap to remove"));}}
    private static String plain(Object content)throws Exception {
        if(content instanceof String)return (String)content;
        if(content instanceof JSONArray){JSONArray a=(JSONArray)content;StringBuilder s=new StringBuilder();for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);if(p.optString("type").equals("text"))s.append(p.optString("text"));else if(p.optString("type").equals("image_url"))s.append("\n[Image / صورة]");}return s.toString();}return "";
    }
    private void renderMessages()throws Exception {
        if(messageList==null)return;
        boolean atBottom=chatScroll.getChildAt(0).getHeight()-chatScroll.getHeight()-chatScroll.getScrollY()<dp(160);int oldY=chatScroll.getScrollY();messageList.removeAllViews();
        JSONObject thread=thread();JSONArray messages=thread.getJSONArray("messages");int lastUser=-1;
        for(int i=0;i<messages.length();i++)if(messages.getJSONObject(i).optString("role").equals("user"))lastUser=i;
        for(int i=0;i<messages.length();i++){
            JSONObject m=messages.getJSONObject(i);boolean user=m.optString("role").equals("user"),pending=m.optString("status").equals("pending"),queued=m.optString("status").equals("queued");
            LinearLayout outer=column();LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.bottomMargin=dp(24);messageList.addView(outer,op);
            if(!user&&Conversations.group(thread)){
                LinearLayout by=row();JSONObject identity=new JSONObject().put("avatar",m.optString("avatar","🤖")).put("photo",m.optString("photo"));by.addView(avatar(identity,18),new LinearLayout.LayoutParams(dp(18),dp(18)));TextView name=label(m.optString("botName","Hermes"),11,MUTED);name.setPadding(dp(6),0,dp(6),0);by.addView(name);outer.addView(by);gap(outer,9);
            }
            boolean active=pending&&currentThread.equals(ChatService.threadId)&&m.optString("id").equals(ChatService.replyId);
            String value=active?ChatService.text:plain(m.opt("content"));
            String modelTag=m.optString("model",m.optString("modelOverride", ""));
            if(!user&&!modelTag.isEmpty()){TextView tag=label("·  "+modelTag,10,MUTED);tag.setPadding(0,0,0,dp(5));outer.addView(tag);}
            if(!value.isEmpty()){
                TextView content=label("",16,user?BG:WHITE);content.setLineSpacing(dp(3),1);content.setTextIsSelectable(true);content.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);content.setText(markdown(value));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(user?-2:-1,-2);cp.gravity=Gravity.END;
                if(user){content.setMaxWidth(getResources().getDisplayMetrics().widthPixels-dp(85));content.setPadding(dp(14),dp(10),dp(14),dp(10));content.setBackground(shape(WHITE,0,21));}
                outer.addView(content,cp);
            }
            JSONArray tools=active?new JSONArray(ChatService.activity):m.optJSONArray("tools");
            if(tools!=null&&tools.length()>0){
                gap(outer,8);TextView summary=label("›  "+tr("نشاط الأدوات","Tool activity")+"  ·  "+tools.length(),12,MUTED);summary.setMinHeight(dp(36));summary.setGravity(Gravity.CENTER_VERTICAL);final JSONArray events=tools;
                summary.setOnClickListener(v->{StringBuilder detail=new StringBuilder();for(int n=0;n<events.length();n++)detail.append("• ").append(events.optString(n)).append("\n");alert(tr("الأدوات المستخدمة","Observed tool activity"),detail.toString());});outer.addView(summary);
            }
            if(pending||queued){gap(outer,5);outer.addView(label(queued?tr("في الانتظار…","Queued…"):active&&!ChatService.progress.isEmpty()?ChatService.progress:tr("● جاري الرد…","● Working…"),12,MUTED));}
            if(m.optString("status").equals("error")){
                gap(outer,8);outer.addView(label(m.optString("error"),12,0xffed7171));
                if(i>lastUser){gap(outer,6);TextView retry=label(tr("إعادة المحاولة","Retry"),13,WHITE);retry.setMinHeight(dp(40));retry.setGravity(Gravity.CENTER_VERTICAL);retry.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(tr("إعادة إرسال الطلب؟","Retry this bot?"))
                    .setMessage(tr("راجع حالة المهمة الأول؛ إعادة الطلب ممكن تكرر أدوات نفّذها البوت.","Check the task first: retrying may repeat tool actions already performed."))
                    .setNegativeButton(tr("رجوع","Cancel"),null).setPositiveButton(tr("إعادة","Retry"),(d,n)->act(()->sendTo(m.optString("botId",thread.optString("botId"))))).show());outer.addView(retry);}
            }
        }
        sendButton.setEnabled(!ChatService.isActive());sendButton.setAlpha(ChatService.isActive()?.3f:1f);
        if(atBottom||messages.length()<=2)chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));else chatScroll.post(()->chatScroll.scrollTo(0,oldY));
    }

    private CharSequence markdown(String input){
        SpannableStringBuilder out=new SpannableStringBuilder();String[] pieces=input.split("```",-1);
        for(int i=0;i<pieces.length;i++){
            String value=pieces[i];if(i%2==1){int newline=value.indexOf('\n');if(newline>=0 && newline<25)value=value.substring(newline+1);}
            int start=out.length();out.append(value);
            if(i%2==1){out.setSpan(new TypefaceSpan("monospace"),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);out.setSpan(new BackgroundColorSpan(0xff242424),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);out.setSpan(new RelativeSizeSpan(.88f),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        }
        return out;
    }
    private void send(boolean retry)throws Exception {sendTo(null);}
    private void sendTo(String retryBot)throws Exception {
        if(ChatService.isActive())return;
        String message=composer.getText().toString().trim();if(retryBot==null&&message.isEmpty()&&attached.isEmpty())return;
        if(message.length()>16000)throw new Exception("Message limit is 16,000 characters");
        JSONObject t=thread();JSONArray bots=store.read().getJSONArray("bots");
        java.util.List<String> recipients=retryBot==null?Conversations.recipients(t,bots,message):java.util.Collections.singletonList(retryBot);
        for(String id:recipients){Store.find(bots,id);if(!Conversations.contains(t,id))throw new Exception("This bot is not in this conversation");}
        final String id=currentThread,image=attached,turn=Store.id();if(!ChatService.reserve())return;
        try{store.edit(d->{
            JSONObject target=Store.find(d.getJSONArray("threads"),id);JSONArray rows=target.getJSONArray("messages");
            if(retryBot==null){Object content=message;
                if(!image.isEmpty())content=new JSONArray().put(new JSONObject().put("type","text").put("text",message.isEmpty()?"Describe this image":message)).put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",image)));
                rows.put(new JSONObject().put("id",Store.id()).put("turn",turn).put("role","user").put("content",content).put("status","done"));
                if(rows.length()==1&&!Conversations.group(target))target.put("title",message.isEmpty()?tr("صورة","Image"):message.substring(0,Math.min(45,message.length())));
            }
            for(String botId:recipients)rows.put(Conversations.pending(Store.find(d.getJSONArray("bots"),botId),turn));target.put("updated",System.currentTimeMillis());
        });}catch(Exception e){ChatService.releaseReservation();throw e;}
        try{startForegroundService(new Intent(this,ChatService.class).putExtra("thread",id).putExtra("turn",turn));}
        catch(Exception e){ChatService.releaseReservation();store.edit(d->{JSONArray rows=Store.find(d.getJSONArray("threads"),id).getJSONArray("messages");for(int j=0;j<rows.length();j++)if(rows.getJSONObject(j).optString("turn").equals(turn)&&rows.getJSONObject(j).optString("status").equals("queued"))rows.getJSONObject(j).put("status","error").put("error","Could not start reply service");});throw e;}
        if(retryBot==null){draft="";attached="";composer.setText("");updateAttachment();}renderMessages();
    }

    private void switchModel()throws Exception {
        JSONObject t=thread();if(Conversations.group(t))throw new Exception(tr("تغيير الموديل متاح للمحادثات الفردية فقط.","Model switching is available for direct chats only."));
        JSONObject b=bot(t.getString("botId"));LinearLayout form=column();form.setPadding(dp(22),dp(12),dp(22),dp(12));
        EditText model=field(form,"MODEL",b.optString("model","hermes-agent"),false);EditText provider=field(form,"PROVIDER",b.optString("provider",""),false);EditText effort=field(form,"REASONING EFFORT",b.optString("effort",""),false);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(tr("تغيير الموديل للمحادثة القادمة","Switch model for next reply")).setView(form).setNegativeButton(tr("رجوع","Cancel"),null).setPositiveButton(tr("حفظ","Save"),null).create();
        d.setOnShowListener(v -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> act(() -> {
                String m=model.getText().toString().trim();
                if(m.isEmpty()) throw new Exception("Enter a model");
                store.edit(x -> Store.find(x.getJSONArray("bots"),b.getString("id"))
                    .put("model",m).put("provider",provider.getText().toString().trim()).put("effort",effort.getText().toString().trim()));
                d.dismiss(); show();
            }));
        });
        d.show();
    }
    private void compareModels()throws Exception {
        JSONObject t=thread();if(Conversations.group(t))throw new Exception(tr("المقارنة متاحة للمحادثات الفردية فقط.","Comparison is available for direct chats only."));
        String message=composer==null?"":composer.getText().toString().trim();if(message.isEmpty())throw new Exception(tr("اكتب رسالة للمقارنة أولًا.","Enter a message to compare first."));
        LinearLayout form=column();form.setPadding(dp(22),dp(12),dp(22),dp(12));EditText one=field(form,"MODEL A",bot(t.getString("botId")).optString("model","hermes-agent"),false);EditText two=field(form,"MODEL B","",false);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(tr("مقارنة موديلين","Compare two models")).setView(form).setNegativeButton(tr("رجوع","Cancel"),null).setPositiveButton(tr("تشغيل المقارنة","Compare"),null).create();
        d.setOnShowListener(v -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> act(() -> {
                String a=one.getText().toString().trim(),b=two.getText().toString().trim();
                if(a.isEmpty()||b.isEmpty()||a.equals(b)) throw new Exception("Enter two different models");
                sendComparison(message,a,b); d.dismiss();
            }));
        });
        d.show();
    }
    private void sendComparison(String message,String modelA,String modelB)throws Exception {
        if(ChatService.isActive())return;JSONObject t=thread();JSONObject bot=bot(t.getString("botId"));final String id=currentThread,turn=Store.id();if(!ChatService.reserve())return;
        try{store.edit(d->{JSONObject target=Store.find(d.getJSONArray("threads"),id);target.getJSONArray("messages").put(new JSONObject().put("id",Store.id()).put("turn",turn).put("role","user").put("content",message).put("status","done"));target.getJSONArray("messages").put(Conversations.pending(bot,turn,modelA,bot.optString("provider"),bot.optString("effort")).put("comparison",true));target.getJSONArray("messages").put(Conversations.pending(bot,turn,modelB,bot.optString("provider"),bot.optString("effort")).put("comparison",true));target.put("updated",System.currentTimeMillis());});}
        catch(Exception e){ChatService.releaseReservation();throw e;}
        startForegroundService(new Intent(this,ChatService.class).putExtra("thread",id).putExtra("turn",turn));composer.setText("");draft="";renderMessages();
    }
    private void chatMenu()throws Exception {
        String[] items={tr("تغيير الاسم","Rename"),tr("نسخة من المحادثة","Branch conversation"),tr("تصدير نص المحادثة","Export conversation text"),tr("حذف من الموبايل","Delete from device"),tr("سجل المحادثات","Conversation history"),tr("محادثة جديدة","New conversation"),tr("تغيير الموديل","Switch model"),tr("مقارنة موديلين","Compare models")};
        new AlertDialog.Builder(this).setItems(items,(d,n)->act(()->{
            if(n==0){EditText name=new EditText(this);name.setText(thread().getString("title"));new AlertDialog.Builder(this).setTitle(items[0]).setView(name).setNegativeButton(tr("رجوع","Back"),null).setPositiveButton(tr("حفظ","Save"),(a,b)->act(()->{String title=name.getText().toString().trim();if(title.isEmpty())return;store.edit(x->Store.find(x.getJSONArray("threads"),currentThread).put("title",title));draft=composer.getText().toString();show();})).show();}
            if(n==1){if(ChatService.isActive())throw new Exception(tr("استنى الرد يخلص.","Wait for the active reply."));JSONObject copy=thread();String id=Store.id();copy.put("id",id).put("title",copy.getString("title")+tr(" · نسخة"," · branch")).put("updated",System.currentTimeMillis());store.edit(x->x.getJSONArray("threads").put(copy));currentThread=id;draft="";attached="";show();}
            if(n==2){StringBuilder text=new StringBuilder(thread().getString("title")+"\n\n");JSONArray rows=thread().getJSONArray("messages");for(int i=0;i<rows.length();i++){JSONObject m=rows.getJSONObject(i);text.append(m.optString("role")).append(":\n").append(plain(m.opt("content"))).append("\n\n");}exportText=text.toString();startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"hermes-conversation.txt"),42);}
            if(n==3){if(ChatService.isActive())throw new Exception(tr("استنى الرد يخلص.","Wait for the active reply."));new AlertDialog.Builder(this).setTitle(items[3]).setMessage(tr("هتتحذف المحادثة من التطبيق فقط.","This deletes the conversation from this app."))
                .setNegativeButton(tr("رجوع","Back"),null).setPositiveButton(tr("حذف","Delete"),(a,b)->act(()->{store.edit(x->{JSONArray rows=x.getJSONArray("threads");for(int i=rows.length()-1;i>=0;i--)if(rows.getJSONObject(i).getString("id").equals(currentThread))rows.remove(i);});page="bots";draft="";attached="";show();})).show();}
            if(n==4){
                JSONObject current=thread();JSONArray rows=store.read().getJSONArray("threads");ArrayList<JSONObject> related=new ArrayList<>();
                for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(Conversations.members(r).toString().equals(Conversations.members(current).toString()))related.add(r);}
                related.sort((a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));String[] titles=new String[related.size()];for(int i=0;i<titles.length;i++)titles[i]=related.get(i).getString("title");
                new AlertDialog.Builder(this).setTitle(items[4]).setItems(titles,(dialog,index)->act(()->{currentThread=related.get(index).getString("id");draft="";attached="";show();})).show();
            }
            if(n==5){JSONObject current=thread();if(Conversations.group(current)){currentThread=store.createGroup(Conversations.members(current),current.getString("title"));draft="";attached="";show();}else newThread(current.getString("botId"));}
            if(n==6)switchModel();
            if(n==7)compareModels();
        })).show();
    }
    private void pickImage(){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),41);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==42)act(()->{if(exportText.isEmpty())throw new Exception("Export expired. Please start export again.");try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Could not open destination");out.write(exportText.getBytes(StandardCharsets.UTF_8));}Toast.makeText(this,tr("تم التصدير","Exported"),Toast.LENGTH_SHORT).show();});
        if(request==44)act(()->{if(exportText.isEmpty())throw new Exception("Export expired. Please start export again.");try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Could not open destination");out.write(exportText.getBytes(StandardCharsets.UTF_8));}Toast.makeText(this,tr("تم تصدير الإعدادات","Settings exported"),Toast.LENGTH_SHORT).show();});
        if(request==45)importBotSettings(uri);
        if(request==43){new Thread(()->{try{
            byte[] bytes;try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                if(in==null)throw new IOException("Cannot read photo");byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>5*1024*1024)throw new Exception("Photo limit is 5 MB");}bytes=out.toByteArray();
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/256);Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);if(image==null)throw new Exception("Unsupported photo");
            float scale=Math.min(1f,256f/Math.max(image.getWidth(),image.getHeight()));Bitmap small=Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*scale)),Math.max(1,Math.round(image.getHeight()*scale)),true);
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.PNG,100,encoded);String photo=android.util.Base64.encodeToString(encoded.toByteArray(),android.util.Base64.NO_WRAP);
            runOnUiThread(()->{if(!isDestroyed()&&openWizard!=null&&openWizard.dialog.isShowing())act(()->openWizard.photo(photo));});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())alert("Photo",e.getMessage());});}}).start();}
        if(request==41){new Thread(()->{try{
            String mime=getContentResolver().getType(uri);if(!Arrays.asList("image/jpeg","image/png","image/webp").contains(mime))throw new Exception("Use a JPEG, PNG or WebP image");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot read image");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>5*1024*1024)throw new Exception("Image limit is 5 MB");}}
            String value="data:"+mime+";base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
            runOnUiThread(()->{if(!isDestroyed()){attached=value;updateAttachment();}});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())alert(tr("الصورة لم تُرفق","Image not attached"),e.getMessage());});}}).start();}
    }
    private final class BotWizard {
        private final JSONObject data;
        private final boolean editing;
        private final LinkedHashMap<String,EditText> inputs=new LinkedHashMap<>();
        private Dialog dialog;
        private int step;
        BotWizard(JSONObject original)throws Exception {
            editing=original!=null;step=editing?-1:0;
            if(editing)data=new JSONObject(original.toString());
            else{
                data=new JSONObject().put("id",Store.id()).put("name","").put("avatar","🤖").put("model","hermes-agent");
                JSONObject server=store.read().optJSONObject("server");if(server!=null)data.put("url",server.optString("url")).put("key",server.optString("key"));
            }
        }
        void show()throws Exception {dialog=new Dialog(MainActivity.this,R.style.AppTheme);render();dialog.show();dialog.getWindow().setLayout(-1,-1);if(Build.VERSION.SDK_INT>=30)dialog.getWindow().setDecorFitsSystemWindows(false);}
        private void collect()throws Exception {for(Map.Entry<String,EditText> e:inputs.entrySet())data.put(e.getKey(),e.getValue().getText().toString().trim());}
        private EditText input(LinearLayout p,String key,String title,String hint,boolean secret){
            EditText e=field(p,title,data.optString(key),secret);e.setHint(hint);inputs.put(key,e);return e;
        }
        private void identity(LinearLayout p)throws Exception {
            if(!editing){
                LinearLayout chips=row();String[] roles={"Researcher","Coder","Writer","Analyst"};
                for(String role:roles){TextView chip=label(role,11,MUTED);chip.setPadding(dp(10),dp(9),dp(10),dp(9));chip.setBackground(shape(BG,LINE,20));chip.setOnClickListener(v->act(()->{
                    collect();data.put("description",role.equals("Coder")?"Writes, reviews and explains code":role.equals("Researcher")?"Researches questions and checks sources":role.equals("Writer")?"Writes and edits clear copy":"Analyzes information and explains findings");data.put("personality",role.equals("Coder")?"Be precise, show tested code, and explain tradeoffs.":role.equals("Researcher")?"Separate facts from hypotheses and cite sources when available.":role.equals("Writer")?"Write clear, polished copy and match the requested tone.":"Break problems into evidence-based steps and state uncertainty.");render();
                }));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginEnd(dp(5));chips.addView(chip,lp);}p.addView(chips);gap(p,20);
            }
            input(p,"name",tr("الاسم","NAME"),"Patch",false);
            input(p,"description",tr("دوره إيه؟","WHAT IS ITS JOB?"),tr("وصف قصير لدور البوت","A short description of this bot's role"),false);
            if(editing)input(p,"handle",tr("اسم الإشارة","MENTION HANDLE"),Conversations.handle(data),false);
        }
        private void personality(LinearLayout p){
            p.addView(label(tr("تعليمات إضافية لمحادثات التطبيق. اتركها فارغة لاستخدام شخصية البوت الموجودة.","Additional instructions for app conversations. Leave empty to use your existing bot's personality."),14,MUTED));gap(p,18);
            EditText e=input(p,"personality","PERSONALITY",tr("إزاي تحب البوت يفكر ويتكلم؟","How should this bot think and communicate?"),false);e.setSingleLine(false);e.setMinLines(9);e.setGravity(Gravity.TOP);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            gap(p,10);input(p,"memory",tr("ذاكرة البوت","BOT MEMORY"),tr("معلومات ثابتة يفتكرها البوت دائمًا","Facts this bot should remember"),false);
        }
        private void models(LinearLayout p)throws Exception {
            TextView defaults=button(tr("استخدم موديل Hermes الافتراضي","Use Hermes default"),false,()->{collect();data.put("model","hermes-agent").put("provider","").put("effort","");render();});p.addView(defaults);gap(p,8);
            p.addView(label(tr("بدون تثبيت موديل — يتبع إعداد السيرفر.","No pin — follows your main Hermes config."),12,MUTED));gap(p,22);
            EditText model=input(p,"model",tr("الموديل","MODEL"),"hermes-agent",false);
            input(p,"provider","PROVIDER",tr("اختياري","Optional"),false);
            input(p,"effort",tr("مستوى التفكير","REASONING EFFORT"),"low / medium / high",false);
            gap(p,10);p.addView(label(tr("موديل بديل عند فشل الموديل الأساسي","FALLBACK MODEL"),12,MUTED));gap(p,6);
            input(p,"fallbackModel","",tr("اختياري — مثال: hermes-agent","Optional — e.g. hermes-agent"),false);
            input(p,"fallbackProvider","FALLBACK PROVIDER",tr("اختياري","Optional"),false);
            input(p,"fallbackEffort","FALLBACK EFFORT","low / medium / high",false);
            TextView load=button(tr("تحميل الموديلات من السيرفر","Load models from server"),false,()->{});
            load.setOnClickListener(v->act(()->{
                collect();if(data.optString("url").isEmpty()||data.optString("key").isEmpty())throw new Exception(tr("اضبط اتصال السيرفر من الإعدادات الأول.","Set the default server connection in Settings first."));load.setEnabled(false);
                JSONObject configuration=new JSONObject(data.toString());new Thread(()->{try{JSONArray options=HermesApi.models(configuration);String[] names=new String[options.length()];for(int i=0;i<names.length;i++)names[i]=options.getJSONObject(i).getString("id");
                    runOnUiThread(()->{if(isDestroyed()||!dialog.isShowing())return;load.setEnabled(true);new AlertDialog.Builder(MainActivity.this).setTitle(tr("موديلات السيرفر","Server models")).setItems(names,(d,n)->model.setText(names[n])).show();});
                }catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;load.setEnabled(true);alert(tr("تعذّر تحميل الموديلات","Could not load models"),e.getMessage());});}}).start();
            }));p.addView(load);
        }
        private void look(LinearLayout p)throws Exception {
            p.addView(label("EMOJI",11,MUTED));gap(p,12);GridLayout grid=new GridLayout(MainActivity.this);grid.setColumnCount(6);
            for(String emoji:new String[]{"🧠","🤖","🦊","👻","🚀","🔭","⚡","🎯","📊","🎨","🦉","🍋"}){
                TextView choice=label(emoji,25,WHITE);choice.setGravity(Gravity.CENTER);choice.setBackground(shape(emoji.equals(data.optString("avatar"))&&data.optString("photo").isEmpty()?0xff262626:BG,emoji.equals(data.optString("avatar"))?LINE:0,12));
                GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=dp(40);lp.height=dp(44);lp.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(choice,lp);
                choice.setOnClickListener(v->act(()->{collect();data.put("avatar",emoji).remove("photo");render();}));
            }p.addView(grid);gap(p,24);p.addView(label(tr("أو صورة","OR A PHOTO"),11,MUTED));gap(p,12);
            p.addView(button(tr("اختيار صورة","Choose photo"),false,()->{collect();startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),43);}));gap(p,22);
            p.addView(label(tr("في قائمة البوتات","IN YOUR ROSTER"),11,MUTED));gap(p,10);
            LinearLayout preview=row();preview.setPadding(dp(13),dp(13),dp(13),dp(13));preview.setBackground(shape(CARD,0,16));preview.addView(avatar(data,42),new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout words=column();words.setPadding(dp(12),0,0,0);words.addView(label(data.optString("name","Bot"),16,WHITE));TextView role=label(data.optString("description"),12,MUTED);role.setMaxLines(2);words.addView(role);preview.addView(words,new LinearLayout.LayoutParams(0,-2,1));p.addView(preview);
        }
        private void connection(LinearLayout p){
            gap(p,24);p.addView(label(tr("اتصال Hermes الموجود","EXISTING HERMES CONNECTION"),11,MUTED));gap(p,12);
            input(p,"url","HTTPS URL","https://birella.your-tailnet.ts.net",false);
            input(p,"profile",tr("Profile على السيرفر","SERVER PROFILE"),tr("default أو اسم Profile عند تفعيل multiplex_profiles","default or a multiplexed profile name"),false);
            input(p,"key","API KEY",tr("مفتاح Hermes API","Hermes API key"),true);
            TextView test=button(tr("اختبار اتصال البوت","Test bot connection"),false,()->{});test.setOnClickListener(v->act(()->{collect();JSONObject check=new JSONObject(data.toString());check.put("url",Endpoint.withProfile(check.optString("url"),check.optString("profile")));testConnection(check,test);}));p.addView(test);gap(p,8);
            p.addView(label(tr("Profile لازم يكون موجودًا ومفعّلًا على السيرفر. يمكن أيضًا إدخال المسار يدويًا مثل /p/alice. هذا لا ينشئ Profile جديدًا.","The profile must already exist and multiplex routing must be enabled on the server. You can also enter /p/alice in the URL. This does not create a new server profile."),12,MUTED));
        }
        private void render()throws Exception {
            inputs.clear();LinearLayout panel=column();panel.setBackgroundColor(BG);panel.setLayoutDirection(arabic?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
            panel.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}return insets;});
            LinearLayout top=row();top.setPadding(dp(12),dp(12),dp(16),dp(10));TextView close=label("×",28,MUTED);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->dialog.dismiss());top.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
            if(!editing){LinearLayout dots=row();dots.setGravity(Gravity.CENTER);for(int i=0;i<4;i++){View dot=new View(MainActivity.this);dot.setBackground(shape(i==step?WHITE:0xff393939,0,8));LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(dp(i==step?17:5),dp(5));d.setMargins(dp(3),0,dp(3),0);dots.addView(dot,d);}top.addView(dots,new LinearLayout.LayoutParams(0,-2,1));top.addView(new View(MainActivity.this),new LinearLayout.LayoutParams(dp(44),1));}panel.addView(top);
            ScrollView scroll=new ScrollView(MainActivity.this);LinearLayout content=column();content.setPadding(dp(24),dp(12),dp(24),dp(20));scroll.addView(content);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            String title=editing?data.optString("name"):step==0?tr("مين البوت ده؟","Who is this bot?"):step==1?tr("الشخصية","Personality"):step==2?tr("الموديل","Model"):tr("الشكل","Look");
            TextView heading=label(title,25,WHITE);heading.setTypeface(null,Typeface.BOLD);content.addView(heading);gap(content,8);
            String subtitle=editing?data.optString("description"):step==0?tr("اختار له اسم ودور.","Name it and give it a job."):step==1?tr("اختياري — حدد طريقة تفكيره وكلامه.","Optional — shape how it thinks and talks."):step==2?tr("اختياري — اختار موديل أو استخدم الافتراضي.","Optional — pin a model, or use your default."):tr("اختار شكله في قائمة البوتات.","Pick how it shows up in your roster.");content.addView(label(subtitle,14,MUTED));gap(content,28);
            if(editing){look(content);gap(content,24);identity(content);gap(content,10);personality(content);gap(content,20);models(content);connection(content);gap(content,25);content.addView(button(tr("إزالة البوت من التطبيق","Remove bot from app"),false,()->remove()));}
            else if(step==0)identity(content);else if(step==1)personality(content);else if(step==2)models(content);else{look(content);connection(content);}
            LinearLayout footer=row();footer.setPadding(dp(24),dp(12),dp(24),dp(22));
            if(!editing&&step>0){TextView prev=label(tr("رجوع","Back"),15,WHITE);prev.setGravity(Gravity.CENTER);prev.setOnClickListener(v->act(()->{collect();step--;render();}));footer.addView(prev,new LinearLayout.LayoutParams(dp(70),dp(48)));}
            footer.addView(button(editing?tr("حفظ","Save"):step==3?tr("إنشاء البوت","Create Bot"):tr("التالي","Next"),true,()->{
                collect();if(step==0&&data.optString("name").isEmpty())throw new Exception(tr("اكتب اسم البوت.","Enter a bot name."));
                if(editing||step==3)save();else{step++;render();}
            }),new LinearLayout.LayoutParams(0,-2,1));panel.addView(footer);dialog.setContentView(panel);panel.requestApplyInsets();
        }
        void photo(String value)throws Exception {data.put("photo",value);render();}
        private void save()throws Exception {
            if(ChatService.isActive())throw new Exception("Wait for the current reply");
            if(data.optString("name").isEmpty())throw new Exception("Enter a bot name");
            data.put("url",Endpoint.withProfile(data.optString("url"),data.optString("profile")));
            String key=data.optString("key");if(key.isEmpty()||key.contains("\n")||key.contains("\r"))throw new Exception("Enter the Hermes API key");
            if(data.optString("model").isEmpty())data.put("model","hermes-agent");
            if(data.optString("memoryKey").isEmpty())data.put("memoryKey","hermes-android:"+data.getString("id"));
            String handle=data.optString("handle");
            if(handle.isEmpty()){
                String stem=data.optString("name").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+","-").replaceAll("^-+|-+$", "");
                handle=stem.matches("[a-z][a-z0-9_-]{0,23}")&&!stem.equals("everyone")?stem:"agent_"+data.getString("id").replace("-", "").substring(0,8);
                JSONArray bots=store.read().getJSONArray("bots");for(int i=0;i<bots.length();i++)if(Conversations.handle(bots.getJSONObject(i)).equals(handle)&&!bots.getJSONObject(i).getString("id").equals(data.getString("id"))){handle+="_"+data.getString("id").substring(0,4);break;}
            }data.put("handle",MentionRouter.handle(handle));
            store.edit(d->{JSONArray bots=d.getJSONArray("bots");for(int i=0;i<bots.length();i++){JSONObject b=bots.getJSONObject(i);if(!b.getString("id").equals(data.getString("id"))&&Conversations.handle(b).equals(data.getString("handle")))throw new Exception("That mention handle is already in use");}
                for(int i=0;i<bots.length();i++)if(bots.getJSONObject(i).getString("id").equals(data.getString("id"))){bots.put(i,new JSONObject(data.toString()));return;}bots.put(new JSONObject(data.toString()));
            });dialog.dismiss();page="bots";MainActivity.this.show();
        }
        private void remove(){new AlertDialog.Builder(MainActivity.this).setTitle(tr("إزالة البوت؟","Remove bot?"))
            .setMessage(tr("هيتحذف الاتصال وكل محادثاته والجروبات التي تحتويه من الموبايل فقط.","Remove this connection and all its local conversations, including groups containing it? The server bot is not deleted."))
            .setNegativeButton(tr("رجوع","Cancel"),null).setPositiveButton(tr("إزالة","Remove"),(a,b)->act(()->{
                if(ChatService.isActive())throw new Exception("Wait for the current reply");String id=data.getString("id");store.edit(d->{JSONArray bots=d.getJSONArray("bots"),threads=d.getJSONArray("threads");for(int i=bots.length()-1;i>=0;i--)if(bots.getJSONObject(i).getString("id").equals(id))bots.remove(i);for(int i=threads.length()-1;i>=0;i--)if(Conversations.contains(threads.getJSONObject(i),id))threads.remove(i);});dialog.dismiss();page="bots";MainActivity.this.show();
            })).show();}
    }
}
