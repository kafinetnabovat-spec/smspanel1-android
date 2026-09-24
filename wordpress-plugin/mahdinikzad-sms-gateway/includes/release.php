<?php
/**
 * بخش «انتشار و به‌روزرسانی» — ساخت نسخه در گیت‌هاب، آپلود APK، و مدیریت به‌روزرسانی اپ
 * افزودن این فایل: require_once __DIR__ . '/includes/release.php';  (در فایل اصلی افزونه انجام شده)
 */
if (!defined('ABSPATH')) exit;

const SMSP1_OPT_GH_TOKEN    = 'smsp1_gh_token';
const SMSP1_OPT_GH_REPO     = 'smsp1_gh_repo';
const SMSP1_OPT_GH_WORKFLOW = 'smsp1_gh_workflow';
const SMSP1_OPT_GH_BRANCH   = 'smsp1_gh_branch';
const SMSP1_OPT_APP_VERSION = 'smsp1_app_version';
const SMSP1_OPT_APP_CHANGELOG = 'smsp1_app_changelog';
const SMSP1_OPT_APP_FORCE   = 'smsp1_app_force';
const SMSP1_OPT_APP_MINCODE = 'smsp1_app_min_code';
const SMSP1_OPT_LAST_BUILD  = 'smsp1_last_build';

function smsp1_gh_repo()     { return trim((string) get_option(SMSP1_OPT_GH_REPO, SMSP1_GITHUB_REPO)); }
function smsp1_gh_token()    { return trim((string) get_option(SMSP1_OPT_GH_TOKEN, '')); }
function smsp1_gh_workflow() { return trim((string) get_option(SMSP1_OPT_GH_WORKFLOW, 'build-and-release.yml')); }
function smsp1_gh_branch()   { return trim((string) get_option(SMSP1_OPT_GH_BRANCH, 'main')); }

/** درخواست به API گیت‌هاب با توکن ذخیره‌شده */
function smsp1_gh_request($method, $path, $body = null) {
    $token = smsp1_gh_token();
    if (!$token) return new WP_Error('no_token', 'توکن گیت‌هاب ثبت نشده است.');
    $args = [
        'method'  => $method,
        'timeout' => 45,
        'headers' => [
            'Authorization'        => 'Bearer ' . $token,
            'Accept'               => 'application/vnd.github+json',
            'X-GitHub-Api-Version' => '2022-11-28',
            'User-Agent'           => 'mahdinikzad-sms-gateway',
        ],
    ];
    if ($body !== null) {
        $args['headers']['Content-Type'] = 'application/json';
        $args['body'] = wp_json_encode($body);
    }
    $res = wp_remote_request('https://api.github.com' . $path, $args);
    if (is_wp_error($res)) return $res;
    $code = (int) wp_remote_retrieve_response_code($res);
    $json = json_decode(wp_remote_retrieve_body($res), true);
    if ($code < 200 || $code > 299) {
        $msg = is_array($json) && !empty($json['message']) ? $json['message'] : ('HTTP ' . $code);
        return new WP_Error('gh_error', $msg, ['status' => $code]);
    }
    return is_array($json) ? $json : [];
}

/** آخرین ریلیز + آخرین APK/پلاگین موجود در آن (با کش ۱۰ دقیقه‌ای) */
function smsp1_gh_latest($force = false) {
    $cache = get_transient('smsp1_gh_latest');
    if (!$force && is_array($cache)) return $cache;
    $res = smsp1_gh_request('GET', '/repos/' . smsp1_gh_repo() . '/releases?per_page=20');
    if (is_wp_error($res)) return $res;
    $out = ['version' => '', 'tag' => '', 'apk_url' => '', 'apk_name' => '', 'plugin_url' => '', 'release_url' => '', 'published_at' => '', 'body' => ''];
    $best = null;
    foreach ($res as $rel) {
        if (!empty($rel['draft'])) continue;
        foreach ((array) ($rel['assets'] ?? []) as $a) {
            $name = (string) ($a['name'] ?? '');
            if (preg_match('/^smspanel1-v?(\d+(?:\.\d+){1,2})[^\/]*\.apk$/i', $name, $m)) {
                if ($best === null || version_compare($m[1], $best['version'], '>')) {
                    $best = ['version' => $m[1], 'tag' => (string) $rel['tag_name'], 'apk_url' => (string) $a['browser_download_url'],
                             'apk_name' => $name, 'apk_size' => (int) $a['size'], 'release_url' => (string) $rel['html_url'],
                             'published_at' => (string) ($rel['published_at'] ?? ''), 'body' => (string) ($rel['body'] ?? '')];
                }
            }
        }
        if ($best !== null) break;
    }
    if ($best === null) {
        // اگر APK پیدا نشد، حداقل تگ آخرین ریلیز را برگردان
        $out['tag'] = (string) ($res[0]['tag_name'] ?? '');
        $out['release_url'] = (string) ($res[0]['html_url'] ?? '');
    } else {
        $out = array_merge($out, $best);
    }
    foreach ($res as $rel) {
        foreach ((array) ($rel['assets'] ?? []) as $a) {
            if (strpos((string) $a['name'], 'mahdinikzad-sms-gateway') === 0 && substr((string) $a['name'], -4) === '.zip') {
                $out['plugin_url'] = (string) $a['browser_download_url'];
                break 2;
            }
        }
    }
    set_transient('smsp1_gh_latest', $out, 10 * MINUTE_IN_SECONDS);
    return $out;
}

/** وضعیت اجراهای اخیر اکشن‌ها (کش ۳۰ ثانیه‌ای تا صفحه کند نشود) */
function smsp1_gh_runs($force = false) {
    $cache = get_transient('smsp1_gh_runs');
    if (!$force && is_array($cache)) return $cache;
    $repo = smsp1_gh_repo();
    $res = smsp1_gh_request('GET', "/repos/$repo/actions/runs?per_page=5");
    if (is_wp_error($res)) return $res;
    $runs = [];
    foreach ((array) ($res['workflow_runs'] ?? []) as $r) {
        $runs[] = [
            'name' => (string) ($r['name'] ?? ''),
            'branch' => (string) ($r['head_branch'] ?? ''),
            'status' => (string) ($r['status'] ?? ''),
            'conclusion' => (string) ($r['conclusion'] ?? ''),
            'created_at' => (string) ($r['created_at'] ?? ''),
            'url' => (string) ($r['html_url'] ?? ''),
        ];
    }
    set_transient('smsp1_gh_runs', $runs, 30);
    return $runs;
}

/** نسخه‌ای که به اپ اعلام می‌شود: تنظیم دستی، وگرنه آخرین ریلیز گیت‌هاب */
function smsp1_app_release_info() {
    $manual = trim((string) get_option(SMSP1_OPT_APP_VERSION, ''));
    $latest = smsp1_gh_latest();
    $ver = $manual !== '' ? $manual : (is_array($latest) ? (string) $latest['version'] : '');
    $url = is_array($latest) ? (string) $latest['apk_url'] : '';
    $log = trim((string) get_option(SMSP1_OPT_APP_CHANGELOG, ''));
    if ($log === '' && is_array($latest)) $log = mb_substr((string) $latest['body'], 0, 1200);
    return [
        'version'      => $ver,
        'version_code' => (int) get_option(SMSP1_OPT_APP_MINCODE, 0),
        'apk_url'      => $url,
        'apk_size'     => is_array($latest) ? (int) ($latest['apk_size'] ?? 0) : 0,
        'changelog'    => $log,
        'force'        => get_option(SMSP1_OPT_APP_FORCE, '0') === '1',
        'released_at'  => is_array($latest) ? (string) $latest['released_at'] ?? '' : '',
        'release_url'  => is_array($latest) ? (string) $latest['release_url'] : '',
        'auto'         => $manual === '' ? 'github' : 'manual',
    ];
}

// ---------------------------------------------------------------- handlers
add_action('admin_post_smsp1_release_save', function () {
    if (!current_user_can('manage_options')) wp_die('دسترسی ندارید');
    check_admin_referer('smsp1_release_save');
    $token = trim((string) ($_POST['gh_token'] ?? ''));
    if ($token !== '') update_option(SMSP1_OPT_GH_TOKEN, $token);   // خالی = بدون تغییر
    update_option(SMSP1_OPT_GH_REPO, sanitize_text_field($_POST['gh_repo'] ?? SMSP1_GITHUB_REPO));
    update_option(SMSP1_OPT_GH_WORKFLOW, sanitize_text_field($_POST['gh_workflow'] ?? 'build-and-release.yml'));
    update_option(SMSP1_OPT_GH_BRANCH, sanitize_text_field($_POST['gh_branch'] ?? 'main'));
    update_option(SMSP1_OPT_APP_VERSION, sanitize_text_field($_POST['app_version'] ?? ''));
    update_option(SMSP1_OPT_APP_CHANGELOG, wp_kses_post($_POST['app_changelog'] ?? ''));
    update_option(SMSP1_OPT_APP_FORCE, isset($_POST['app_force']) ? '1' : '0');
    update_option(SMSP1_OPT_APP_MINCODE, (int) ($_POST['app_min_code'] ?? 0));
    delete_transient('smsp1_gh_latest'); delete_transient('smsp1_gh_runs');
    wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&saved=1'));
    exit;
});

add_action('admin_post_smsp1_release_build', function () {
    if (!current_user_can('manage_options')) wp_die('دسترسی ندارید');
    check_admin_referer('smsp1_release_build');
    $res = smsp1_gh_request('POST', '/repos/' . smsp1_gh_repo() . '/actions/workflows/' . smsp1_gh_workflow() . '/dispatches', ['ref' => smsp1_gh_branch()]);
    if (is_wp_error($res)) {
        $msg = $res->get_error_message();
        update_option(SMSP1_OPT_LAST_BUILD, ['ok' => false, 'msg' => $msg, 'at' => current_time('mysql')]);
        wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&build_error=' . rawurlencode($msg)));
    } else {
        update_option(SMSP1_OPT_LAST_BUILD, ['ok' => true, 'msg' => 'بیلد شروع شد', 'at' => current_time('mysql')]);
        delete_transient('smsp1_gh_runs');
        wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&build=1'));
    }
    exit;
});

add_action('admin_post_smsp1_release_upload', function () {
    if (!current_user_can('manage_options')) wp_die('دسترسی ندارید');
    check_admin_referer('smsp1_release_upload');
    if (empty($_FILES['apk']['tmp_name'])) {
        wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&upload_error=' . rawurlencode('فایلی انتخاب نشد')));
        exit;
    }
    $tmp = $_FILES['apk']['tmp_name'];
    $name = sanitize_file_name($_FILES['apk']['name'] ?: 'app.apk');
    $ver = trim((string) ($_POST['upload_version'] ?? ''));
    if ($ver === '') {
        $info = smsp1_app_release_info();
        $ver = (string) $info['version'];
    }
    if (!preg_match('/^\d+(\.\d+){1,2}$/', $ver)) {
        wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&upload_error=' . rawurlencode('شماره نسخه نامعتبر است (مثل 8.2.0)')));
        exit;
    }
    // نام asset باید الگوی smspanel1-v<version>.apk را داشته باشد تا اپ آن را پیدا کند
    $asset = 'smspanel1-v' . $ver . '.apk';
    $repo = smsp1_gh_repo();
    $tag = 'v' . $ver;
    // ۱) ریلیز وجود دارد؟
    $rel = smsp1_gh_request('GET', "/repos/$repo/releases/tags/$tag");
    if (is_wp_error($rel)) {
        $rel = smsp1_gh_request('POST', "/repos/$repo/releases", [
            'tag_name' => $tag, 'name' => 'SmsPanel1 v' . $ver, 'body' => 'انتشار از پنل وردپرس', 'draft' => false,
        ]);
    }
    if (is_wp_error($rel)) {
        wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&upload_error=' . rawurlencode($rel->get_error_message())));
        exit;
    }
    $upload_url = $rel['upload_url'] ?? '';
    $upload_url = str_replace('{?name,label}', '?name=' . rawurlencode($asset), $upload_url);
    if (!$upload_url) {
        wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&upload_error=' . rawurlencode('upload_url پیدا نشد')));
        exit;
    }
    $data = file_get_contents($tmp);
    $res = wp_remote_post($upload_url, [
        'timeout' => 300,
        'headers' => [
            'Authorization'  => 'Bearer ' . smsp1_gh_token(),
            'Content-Type'   => 'application/vnd.android.package-archive',
            'Content-Length' => strlen($data),
            'User-Agent'     => 'mahdinikzad-sms-gateway',
        ],
        'body' => $data,
    ]);
    if (is_wp_error($res)) {
        wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&upload_error=' . rawurlencode($res->get_error_message())));
        exit;
    }
    $code = (int) wp_remote_retrieve_response_code($res);
    delete_transient('smsp1_gh_latest');
    if ($code < 200 || $code > 299) {
        wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&upload_error=' . rawurlencode('آپلود ناموفق (HTTP ' . $code . ')')));
    } else {
        wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=release&uploaded=' . rawurlencode($asset)));
    }
    exit;
});

// ---------------------------------------------------------------- render
function smsp1_render_release_tab() {
    if (!current_user_can('manage_options')) return;

    $token_set = smsp1_gh_token() !== '';
    $info      = smsp1_app_release_info();
    $latest    = $token_set ? smsp1_gh_latest() : new WP_Error('x', 'بدون توکن');
    $runs      = $token_set ? smsp1_gh_runs() : new WP_Error('x', 'بدون توکن');
    $last      = get_option(SMSP1_OPT_LAST_BUILD, []);
    ?>
    <style>
        .smsp1-form label{display:flex;flex-direction:column;gap:6px;font-weight:600}
        .smsp1-form input[type=text],.smsp1-form input[type=password],.smsp1-form input[type=number],.smsp1-form textarea{padding:8px 10px;border:1px solid #ccd0d4;border-radius:8px;min-width:260px;font-family:inherit}
        .smsp1-form textarea{min-height:110px}
        .smsp1-kv{display:grid;grid-template-columns:170px 1fr;gap:6px 12px;font-size:13px}
        .smsp1-kv b{color:#075E54}
    </style>

    <?php if (isset($_GET['saved'])): ?><div class="notice notice-success"><p>تنظیمات ذخیره شد.</p></div><?php endif; ?>
    <?php if (isset($_GET['build'])): ?><div class="notice notice-success"><p>✅ بیلد در گیت‌هاب شروع شد. چند دقیقه صبر کن و بعد «به‌روزرسانی وضعیت» را بزن.</p></div><?php endif; ?>
    <?php if (isset($_GET['uploaded'])): ?><div class="notice notice-success"><p>✅ فایل آپلود شد: <code><?php echo esc_html($_GET['uploaded']); ?></code></p></div><?php endif; ?>
    <?php if (isset($_GET['build_error'])): ?><div class="notice notice-error"><p>❌ بیلد شروع نشد: <?php echo esc_html(wp_unslash($_GET['build_error'])); ?></p>
        <p><small>اگر پیام «Actions has been disabled for this user» بود، یعنی گیت‌هاب برای اکانت صاحب توکن، اکشن‌ها را غیرفعال کرده — از «آپلود دستی APK» پایین استفاده کن یا توکن را از اکانت دیگری بساز.</small></p></div><?php endif; ?>
    <?php if (isset($_GET['upload_error'])): ?><div class="notice notice-error"><p>❌ آپلود ناموفق: <?php echo esc_html(wp_unslash($_GET['upload_error'])); ?></p></div><?php endif; ?>

    <!-- ۱) وضعیت -->
    <div class="smsp1-card"><h3>۱) وضعیت انتشار</h3>
        <div class="smsp1-kv">
            <b>نسخه‌ای که اپ می‌بیند</b><span><?php echo esc_html($info['version'] ?: '—'); ?> (منبع: <?php echo $info['auto'] === 'manual' ? 'دستی' : 'آخرین ریلیز گیت‌هاب'; ?>)</span>
            <b>لینک دانلود APK</b><span><?php echo $info['apk_url'] ? '<a href="' . esc_url($info['apk_url']) . '" target="_blank" dir="ltr">' . esc_html(basename($info['apk_url'])) . '</a>' : '— هنوز APK منتشر نشده —'; ?></span>
            <b>آخرین ریلیز</b><span><?php echo is_array($latest) && $latest['tag'] ? '<a href="' . esc_url($latest['release_url']) . '" target="_blank">' . esc_html($latest['tag']) . '</a> (' . esc_html($latest['published_at']) . ')' : '—'; ?></span>
            <b>آخرین بیلد از پنل</b><span><?php echo $last ? esc_html(($last['ok'] ? '✅ ' : '❌ ') . $last['msg'] . ' — ' . $last['at']) : '—'; ?></span>
            <b>آخرین اجراهای اکشن</b><span>
            <?php if (is_wp_error($runs)): echo esc_html($runs->get_error_message()); else: ?>
                <?php foreach ($runs as $r): $badge = $r['conclusion'] === 'success' ? 'smsp1-ok' : ($r['status'] === 'in_progress' || $r['status'] === 'queued' ? 'smsp1-warn' : 'smsp1-bad'); ?>
                    <span class="smsp1-badge <?php echo $badge; ?>" style="margin:0 4px 4px 0;display:inline-block">
                        <a href="<?php echo esc_url($r['url']); ?>" target="_blank" style="color:inherit;text-decoration:none">
                        <?php echo esc_html($r['name'] . ' · ' . ($r['conclusion'] ?: $r['status'])); ?>
                        </a></span>
                <?php endforeach; ?>
            <?php endif; ?>
            </span>
        </div>
        <p><a class="button" href="<?php echo esc_url(admin_url('admin.php?page=smsp1-panel&tab=release&refresh=1')); ?>">🔄 به‌روزرسانی وضعیت</a>
        <?php if (isset($_GET['refresh'])) { delete_transient('smsp1_gh_latest'); delete_transient('smsp1_gh_runs'); echo '<small style="color:#0a7d2c;margin-right:8px">تازه شد.</small>'; } ?></p>
        <p style="color:#666;font-size:12px">افزونه در حال حاضر <b>v<?php echo esc_html(SMSP1_VERSION); ?></b> است<?php echo $latest && !is_wp_error($latest) && !empty($latest['plugin_url']) ? ' و <a href="' . esc_url($latest['plugin_url']) . '">آخرین ZIP افزونه</a> در گیت‌هاب موجود است.' : ' — ZIP افزونه در ریلیزها منتشر می‌شود.'; ?></p>
    </div>

    <!-- ۲) تنظیمات گیت‌هاب -->
    <div class="smsp1-card"><h3>۲) اتصال به گیت‌هاب</h3>
        <form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
            <?php wp_nonce_field('smsp1_release_save'); ?>
            <input type="hidden" name="action" value="smsp1_release_save">
            <div class="smsp1-form">
                <label>توکن گیت‌هاب (PAT)
                    <input type="password" name="gh_token" placeholder="<?php echo $token_set ? 'ثبت شده — برای تغییر، توکن جدید را وارد کن' : 'ghp_... یا github_pat_...'; ?>" autocomplete="new-password">
                </label>
                <label>مخزن
                    <input type="text" name="gh_repo" value="<?php echo esc_attr(smsp1_gh_repo()); ?>" dir="ltr" placeholder="owner/repo">
                </label>
                <label>فایل ورک‌فلو
                    <input type="text" name="gh_workflow" value="<?php echo esc_attr(smsp1_gh_workflow()); ?>" dir="ltr">
                </label>
                <label>برنچ
                    <input type="text" name="gh_branch" value="<?php echo esc_attr(smsp1_gh_branch()); ?>" dir="ltr">
                </label>
            </div>
            <p style="color:#a00;font-size:12px;margin-top:10px">⚠️ توکن در دیتابیس وردپرس ذخیره می‌شود؛ فقط برای همین سایت و فقط با اسکوپ‌های لازم بسازش:
            <code>repo</code> + <code>workflow</code> (برای ساخت نسخه) و <code>actions:write</code> (برای شروع بیلد).</p>

            <hr style="margin:16px 0">
            <h4 style="margin:6px 0">به‌روزرسانی داخل اپ</h4>
            <div class="smsp1-form">
                <label>نسخه‌ی اعلامی اپ
                    <input type="text" name="app_version" value="<?php echo esc_attr(get_option(SMSP1_OPT_APP_VERSION, '')); ?>" dir="ltr" placeholder="خالی = خودکار از گیت‌هاب">
                </label>
                <label>حداقل نسخه‌ای که اجباری شود (versionCode)
                    <input type="number" name="app_min_code" value="<?php echo esc_attr(get_option(SMSP1_OPT_APP_MINCODE, 0)); ?>">
                </label>
                <label style="justify-content:flex-end">اجباری بودن به‌روزرسانی
                    <span><input type="checkbox" name="app_force" <?php checked(get_option(SMSP1_OPT_APP_FORCE, '0'), '1'); ?>> کاربر نتواند رد کند</span>
                </label>
            </div>
            <label style="display:block;margin-top:10px;font-weight:600">متن تغییرات (چنج‌لاگ) که در اپ نشان داده می‌شود
                <textarea name="app_changelog" placeholder="خالی بگذاری، از متن ریلیز گیت‌هاب برداشته می‌شود"><?php echo esc_textarea(get_option(SMSP1_OPT_APP_CHANGELOG, '')); ?></textarea>
            </label>
            <p><button class="button button-primary" type="submit">💾 ذخیره‌ی تنظیمات</button></p>
        </form>
        <p style="font-size:12px;color:#666">آدرسی که اپ برای بررسی نسخه صدا می‌زند:
            <code dir="ltr"><?php echo esc_html(home_url('/wp-json/smsp1/v1/app/version')); ?></code>
            — <a href="<?php echo esc_url(home_url('/wp-json/smsp1/v1/app/version')); ?>" target="_blank">همین حالا تست کن</a>
        </p>
    </div>

    <!-- ۳) ساخت نسخه -->
    <div class="smsp1-card"><h3>۳) ساخت نسخه‌ی جدید در گیت‌هاب</h3>
        <p>این دکمه، ورک‌فلوی <code dir="ltr"><?php echo esc_html(smsp1_gh_workflow()); ?></code> را روی برنچ <code dir="ltr"><?php echo esc_html(smsp1_gh_branch()); ?></code> اجرا می‌کند و گیت‌هاب APK + ZIP افزونه را می‌سازد و در Releases می‌گذارد.</p>
        <form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
            <?php wp_nonce_field('smsp1_release_build'); ?>
            <input type="hidden" name="action" value="smsp1_release_build">
            <button class="button button-primary" type="submit" <?php disabled(!$token_set); ?>>🚀 شروع بیلد در گیت‌هاب</button>
            <?php if (!$token_set): ?><small style="margin-right:8px;color:#a00">اول توکن را ذخیره کن.</small><?php endif; ?>
        </form>
        <p style="font-size:12px;color:#666">پیش‌نیاز: اکشن‌های گیت‌هاب برای اکانت صاحب توکن فعال باشد. اگر گیت‌هاب بگوید «Actions has been disabled for this user»، از آپلود دستی پایین استفاده کن.</p>
    </div>

    <!-- ۴) آپلود دستی -->
    <div class="smsp1-card"><h3>۴) آپلود دستی APK (جایگزین بیلد خودکار)</h3>
        <p>فایل APK را از دستگاهت انتخاب کن؛ افزونه آن را به‌عنوان asset با نام <code dir="ltr">smspanel1-v&lt;نسخه&gt;.apk</code> در ریلیز همان نسخه آپلود می‌کند و اپ بلافاصله آن را به‌عنوان به‌روزرسانی می‌بیند.</p>
        <form method="post" enctype="multipart/form-data" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
            <?php wp_nonce_field('smsp1_release_upload'); ?>
            <input type="hidden" name="action" value="smsp1_release_upload">
            <div class="smsp1-form">
                <label>فایل APK
                    <input type="file" name="apk" accept=".apk,application/vnd.android.package-archive" required>
                </label>
                <label>شماره نسخه
                    <input type="text" name="upload_version" dir="ltr" value="<?php echo esc_attr($info['version']); ?>" placeholder="8.2.0">
                </label>
            </div>
            <p><button class="button" type="submit" <?php disabled(!$token_set); ?>>⬆️ آپلود در گیت‌هاب</button></p>
        </form>
    </div>

    <!-- ۵) آنچه اپ دریافت می‌کند -->
    <div class="smsp1-card"><h3>۵) پاسخی که اپ دریافت می‌کند</h3>
        <pre dir="ltr" style="background:#f6f7f9;padding:12px;border-radius:8px;overflow:auto;font-size:12px"><?php echo esc_html(wp_json_encode($info, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE)); ?></pre>
    </div>
    <?php
}
