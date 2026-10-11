package com.luoyilin.focusguard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.*;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.*;
import retrofit2.Call;

public class ProfileActivity extends AppCompatActivity {
    private SessionManager session;
    private long owner;
    private String avatar;
    private EditText name;
    private boolean busy, draft;
    private Call<?> call;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_profile);
        SecondaryPage.show(this, ((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0), "个人资料", () -> getOnBackPressedDispatcher().onBackPressed());
        
        session = new SessionManager(this); owner = state == null ? session.getUserId() : state.getLong("profileOwner");
        if (!ownsPage()) { finish(); return; }
        name = findViewById(R.id.profileName);
        avatar = state == null ? session.getAvatarKey() : state.getString("avatar", session.getAvatarKey());
        draft = state != null;
        name.setText(state == null ? session.getUsername() : state.getString("draftName", session.getUsername()));
        name.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { draft = true; }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        findViewById(R.id.profileChooseAvatar).setOnClickListener(v -> chooseAvatar());
        findViewById(R.id.profileAvatar).setOnClickListener(v -> { if (!busy) chooseAvatar(); });
        findViewById(R.id.profileSave).setOnClickListener(v -> save());
        findViewById(R.id.profileBindEmail).setOnClickListener(v -> { if (ownsPage()) {
            if(!session.hasPassword()&&session.getPhone()==null){toast("请先绑定手机号，再设置邮箱与密码");return;}
            startActivity(new Intent(this, EmailBindingActivity.class));
        } });
        findViewById(R.id.profileBindPhone).setOnClickListener(v -> { if(ownsPage()) startActivity(new Intent(this,PhoneAuthActivity.class).putExtra("binding",true)); });
        findViewById(R.id.profileBindWechat).setOnClickListener(v -> { if(ownsPage())
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this,R.style.HomeDialog).setTitle("绑定微信到当前账号？")
                    .setMessage("绑定后，可使用微信登录当前账号，继续使用原有备份和同步数据。微信若已绑定其他账号，不会自动合并或覆盖。")
                    .setNegativeButton("取消",null).setPositiveButton("前往微信授权",(d,w)->startActivity(new Intent(this,WechatAuthActivity.class).putExtra("binding",true))).show(); });
        findViewById(R.id.profileBack).setOnClickListener(v -> finish());
        render();
    }
    @Override protected void onResume() { super.onResume(); if (name != null && ownsPage()) load(); }
    @Override protected void onDestroy() { if (call != null) call.cancel(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (name != null) { state.putLong("profileOwner", owner); state.putString("avatar", avatar); state.putString("draftName", name.getText().toString()); }
        super.onSaveInstanceState(state);
    }
    private boolean ownsPage() {
        boolean valid = session != null && session.isLoggedIn() && owner == session.getUserId();
        if (!valid && session != null) toast("请使用当前账号重新进入个人资料"); return valid;
    }
    private void render() {
        AvatarCatalog.show(findViewById(R.id.profileAvatar), avatar, 112);
        ((TextView)findViewById(R.id.profileEmail)).setText(session.getEmail()==null?"邮箱 · 未绑定":"邮箱 · 已绑定\n" + session.getEmail());
        ((Button)findViewById(R.id.profileBindEmail)).setText(session.getEmail()==null?"绑定邮箱":"更换绑定邮箱");
        ((TextView)findViewById(R.id.profilePhone)).setText("手机号 · " + PhoneNumberRules.mask(session.getPhone()));
        ((Button)findViewById(R.id.profileBindPhone)).setText(session.getPhone()==null?"绑定手机号":"更换手机号");
        findViewById(R.id.profileBindPhone).setEnabled(!busy);
        ((TextView)findViewById(R.id.profileWechat)).setText(session.isWechatLinked()?"微信 · 已绑定":"微信 · 未绑定");
        ((Button)findViewById(R.id.profileBindWechat)).setText(session.isWechatLinked()?"微信已绑定":"绑定微信");
        findViewById(R.id.profileBindWechat).setEnabled(!busy&&!session.isWechatLinked());
        ((TextView)findViewById(R.id.profileAccountId)).setText("账号 ID · " + owner);
        for (int id : new int[]{R.id.profileName, R.id.profileChooseAvatar, R.id.profileSave, R.id.profileBindEmail}) findViewById(id).setEnabled(!busy);
        findViewById(R.id.profileProgress).setVisibility(busy ? View.VISIBLE : View.GONE);
    }
    private void load() {
        if (busy) return;
        String auth = session.getAuthorizationHeader(); busy = true; render();
        if (call != null) call.cancel();
        Call<ProfileResponse> request = RetrofitClient.getApi().getProfile(auth); call = request;
        request.enqueue(new AccountRequestCallback<>(session, auth, (profile, error) -> {
            if (isFinishing() || isDestroyed()) return;
            busy = false;
            if (error != null) toast(error);
            else if (session.updateProfile(profile, auth) && !draft) {
                name.setText(profile.displayName); avatar = profile.avatarKey; draft = false;
            }
            render();
        }));
    }
    private void save() {
        if (!ownsPage()) return;
        String text = name.getText().toString().trim();
        if (!ProfileNameRules.valid(text)) { name.setError("昵称需为 1–24 个字符，不能包含换行"); return; }
        String auth = session.getAuthorizationHeader(); busy = true; render();
        Call<ProfileResponse> request = RetrofitClient.getApi().updateProfile(auth, new ProfileRequest(text, avatar)); call = request;
        request.enqueue(new AccountRequestCallback<>(session, auth, (profile, error) -> {
            if (isFinishing() || isDestroyed()) return;
            busy = false;
            if (error != null) toast(error);
            else if (session.updateProfile(profile, auth)) { draft = false; toast("个人资料已保存"); finish(); }
            else toast("账号已变化，保存结果未写入本机");
            render();
        }));
    }
    private void chooseAvatar() {
        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL); grid.setPadding(dp(20), dp(12), dp(20), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("选择头像").setView(grid).setNegativeButton("取消", null).create();
        for (int i = 0; i < AvatarCatalog.KEYS.length; i += 2) {
            LinearLayout row = new LinearLayout(this); grid.addView(row);
            for (int j = i; j < Math.min(i + 2, AvatarCatalog.KEYS.length); j++) {
                final int index = j;
                LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(android.view.Gravity.CENTER); tile.setPadding(dp(8), dp(8), dp(8), dp(8));
                ImageView image = new ImageView(this); tile.addView(image, new LinearLayout.LayoutParams(dp(72), dp(72))); AvatarCatalog.show(image, AvatarCatalog.KEYS[j], 72);
                TextView label = new TextView(this); label.setText(AvatarCatalog.NAMES[j] + (AvatarCatalog.KEYS[j].equals(avatar) ? " ✓" : "")); label.setTextColor(getColor(R.color.text_primary)); label.setTextSize(14); tile.addView(label);
                tile.setOnClickListener(v -> { avatar = AvatarCatalog.KEYS[index]; draft = true; render(); dialog.dismiss(); });
                row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
            }
        }
        dialog.show();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
