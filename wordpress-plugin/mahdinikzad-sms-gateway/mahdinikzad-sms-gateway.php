<?php
/**
 * Plugin Name: MahdiNikzad SMS Gateway
 * Plugin URI: https://mahdinikzad.ir
 * Description: بک‌اند اپ SmsPanel — مدیریت لایسنس کاربران، گروه‌بندی، مخاطبین و صف ارسال پیامک.
 * Version: 4.3.0
 * Requires at least: 6.0
 * Requires PHP: 8.0
 * Author: Mahdi Nikzad
 * Author URI: https://mahdinikzad.ir
 * License: GPLv2 or later
 * Text Domain: mahdinikzad-sms-gateway
 * Update URI: https://github.com/kafinetnabovat-spec/smspanel1-android
 */

if (!defined('ABSPATH')) exit;

define('SMSP1_VERSION', '4.3.0');
define('SMSP1_SLUG', 'mahdinikzad-sms-gateway/mahdinikzad-sms-gateway.php');
define('SMSP1_LICENSE_ACTIVE_META', '_smsp1_license_active');
define('SMSP1_LICENSE_EXPIRES_META', '_smsp1_license_expires');
define('SMSP1_TOKEN_META', '_smsp1_api_token');
define('SMSP1_GITHUB_REPO', 'kafinetnabovat-spec/smspanel1-android');

// ---------- helpers ----------
function smsp1_table($name) {
    global $wpdb;
    return $wpdb->prefix . 'smsp1_' . $name;
}

function smsp1_normalize_mobile($m) {
    $m = trim((string) $m);
    $m = preg_replace('/[\s\-]/', '', $m);
    if (str_starts_with($m, '+98')) $m = '0' . substr($m, 3);
    elseif (str_starts_with($m, '0098')) $m = '0' . substr($m, 4);
    elseif (str_starts_with($m, '98')) $m = '0' . substr($m, 2);
    return $m;
}

function smsp1_valid_mobile($m) {
    $m = smsp1_normalize_mobile($m);
    return (bool) preg_match('/^09\d{9}$/', $m) ? $m : false;
}

function smsp1_render_name($template, $name) {
    return str_replace(['{نام}', '{name}'], $name, $template);
}

// ---------- activation ----------
register_activation_hook(__FILE__, 'smsp1_activate');
function smsp1_activate() {
    global $wpdb;
    require_once ABSPATH . 'wp-admin/includes/upgrade.php';
    $charset = $wpdb->get_charset_collate();

    dbDelta("CREATE TABLE " . smsp1_table('groups') . " (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        name VARCHAR(190) NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY user_id (user_id)
    ) $charset;");

    dbDelta("CREATE TABLE " . smsp1_table('contacts') . " (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        group_id BIGINT UNSIGNED NOT NULL,
        name VARCHAR(190) NOT NULL DEFAULT '',
        mobile VARCHAR(20) NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uniq_group_mobile (group_id, mobile),
        KEY user_id (user_id)
    ) $charset;");

    dbDelta("CREATE TABLE " . smsp1_table('campaigns') . " (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        group_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
        title VARCHAR(190) NOT NULL DEFAULT '',
        body TEXT NOT NULL,
        total INT NOT NULL DEFAULT 0,
        sent INT NOT NULL DEFAULT 0,
        failed INT NOT NULL DEFAULT 0,
        status VARCHAR(20) NOT NULL DEFAULT 'pending',
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY user_id (user_id)
    ) $charset;");

    dbDelta("CREATE TABLE " . smsp1_table('queue') . " (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        campaign_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
        receiver VARCHAR(20) NOT NULL,
        body TEXT NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'pending',
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY user_status (user_id, status),
        KEY campaign (campaign_id)
    ) $charset;");

    dbDelta("CREATE TABLE " . smsp1_table('templates') . " (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id BIGINT UNSIGNED NOT NULL,
        title VARCHAR(190) NOT NULL,
        body TEXT NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY user_id (user_id)
    ) $charset;");
}

// ---------- auth ----------
function smsp1_token_user_id(WP_REST_Request $req) {
    // 1) standard header (may be stripped by some shared hosts)
    $token = '';
    $header = $req->get_header('Authorization');
    if ($header && preg_match('/Bearer\s+(\S+)/', $header, $m)) $token = $m[1];
    // 2) fallback header (survives hosts that drop Authorization)
    if (!$token) $token = trim((string) $req->get_header('X-SMSP1-Token'));
    // 3) last-resort param (only over HTTPS; header path is preferred)
    if (!$token) $token = trim((string) $req->get_param('api_token'));
    if (!$token || strlen($token) > 100) return 0;
    global $wpdb;
    $users = get_users(['meta_key' => SMSP1_TOKEN_META, 'meta_value' => $token, 'fields' => ['ID']]);
    if (empty($users)) return 0;
    $uid = (int) $users[0]->ID;
    // license check
    $active = get_user_meta($uid, SMSP1_LICENSE_ACTIVE_META, true) === '1';
    if (!$active) return 0;
    $exp = get_user_meta($uid, SMSP1_LICENSE_EXPIRES_META, true);
    if ($exp && strtotime($exp) < time()) return 0;
    return $uid;
}

function smsp1_require_auth(WP_REST_Request $req) {
    $uid = smsp1_token_user_id($req);
    if (!$uid) return new WP_Error('unauthorized', 'توکن نامعتبر یا لایسنس غیرفعال است', ['status' => 401]);
    return $uid;
}

// ---------- routes ----------
add_action('rest_api_init', function () {
    $ns = 'smsp1/v1';

    register_rest_route($ns, '/login', [
        'methods' => 'POST',
        'permission_callback' => '__return_true',
        'callback' => function (WP_REST_Request $req) {
            $u = sanitize_user($req->get_param('username'));
            $p = (string) $req->get_param('password');
            $user = wp_authenticate($u, $p);
            if (is_wp_error($user)) return new WP_Error('bad_login', 'نام کاربری یا رمز اشتباه است', ['status' => 401]);
            $active = get_user_meta($user->ID, SMSP1_LICENSE_ACTIVE_META, true) === '1';
            if (!$active) return new WP_Error('no_license', 'لایسنس شما فعال نیست', ['status' => 403]);
            $exp = get_user_meta($user->ID, SMSP1_LICENSE_EXPIRES_META, true);
            if ($exp && strtotime($exp) < time()) return new WP_Error('expired', 'لایسنس منقضی شده است', ['status' => 403]);
            $token = wp_generate_password(48, false);
            update_user_meta($user->ID, SMSP1_TOKEN_META, $token);
            return ['user_id' => $user->ID, 'api_token' => $token];
        },
    ]);

    // Diagnostic: نشان می‌دهد کدام کانال توکن به PHP می‌رسد (مقدارها هیچ‌وقت چاپ نمی‌شوند).
    // برای ادمین‌های وردپرس محدود شد — قبلاً عمومی بود و اطلاعات کانال‌های auth را لو می‌داد.
    register_rest_route($ns, '/diag', [
        'methods' => 'GET',
        'permission_callback' => function () { return current_user_can('manage_options'); },
        'callback' => function (WP_REST_Request $req) {
            $auth = (string) $req->get_header('Authorization');
            $x = (string) $req->get_header('X-SMSP1-Token');
            $p = (string) $req->get_param('api_token');
            return [
                'version' => SMSP1_VERSION,
                'ssl' => is_ssl(),
                'auth_header_seen' => $auth !== '',
                'auth_is_bearer' => (bool) preg_match('/Bearer\s+\S+/', $auth),
                'x_token_seen' => $x !== '',
                'param_seen' => $p !== '',
            ];
        },
    ]);

    $authed = function (callable $cb) {
        return function (WP_REST_Request $req) use ($cb) {
            $uid = smsp1_require_auth($req);
            if (is_wp_error($uid)) return $uid;
            return $cb($req, $uid);
        };
    };
    $perm = function (WP_REST_Request $req) {
        return !is_wp_error(smsp1_require_auth($req));
    };

    register_rest_route($ns, '/groups', [
        ['methods' => 'GET', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            return $wpdb->get_results($wpdb->prepare("SELECT id, name FROM " . smsp1_table('groups') . " WHERE user_id=%d ORDER BY id DESC", $uid), ARRAY_A);
        })],
        ['methods' => 'POST', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            $name = sanitize_text_field($req->get_param('name'));
            if (!$name) return new WP_Error('bad', 'نام گروه لازم است', ['status' => 400]);
            $wpdb->insert(smsp1_table('groups'), ['user_id' => $uid, 'name' => $name]);
            return ['id' => (int) $wpdb->insert_id, 'name' => $name];
        })],
    ]);

    // قالب‌های پیام (قبلاً اپ به این روت نیاز داشت ولی در پلاگین وجود نداشت)
    register_rest_route($ns, '/templates', [
        ['methods' => 'GET', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            return $wpdb->get_results($wpdb->prepare("SELECT id, title, body FROM " . smsp1_table('templates') . " WHERE user_id=%d ORDER BY id DESC LIMIT 100", $uid), ARRAY_A);
        })],
        ['methods' => 'POST', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            $title = sanitize_text_field($req->get_param('title'));
            $body = (string) $req->get_param('body');
            if (!$title || $body === '') return new WP_Error('bad', 'title و body لازم است', ['status' => 400]);
            $wpdb->insert(smsp1_table('templates'), ['user_id' => $uid, 'title' => $title, 'body' => $body]);
            return ['id' => (int) $wpdb->insert_id, 'title' => $title];
        })],
        ['methods' => 'DELETE', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            $id = (int) $req->get_param('id');
            if ($id <= 0) return new WP_Error('bad', 'id لازم است', ['status' => 400]);
            $n = $wpdb->delete(smsp1_table('templates'), ['id' => $id, 'user_id' => $uid]);
            return ['deleted' => (int) $n];
        })],
    ]);

    register_rest_route($ns, '/contacts', [
        ['methods' => 'GET', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            $gid = (int) $req->get_param('group_id');
            if ($gid > 0) {
                return $wpdb->get_results($wpdb->prepare("SELECT id, group_id, name, mobile FROM " . smsp1_table('contacts') . " WHERE user_id=%d AND group_id=%d ORDER BY id DESC LIMIT 2000", $uid, $gid), ARRAY_A);
            }
            return $wpdb->get_results($wpdb->prepare("SELECT id, group_id, name, mobile FROM " . smsp1_table('contacts') . " WHERE user_id=%d ORDER BY id DESC LIMIT 2000", $uid), ARRAY_A);
        })],
        ['methods' => 'POST', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            $gid = (int) $req->get_param('group_id');
            if ($gid <= 0) return new WP_Error('bad', 'group_id لازم است', ['status' => 400]);
            // owner check
            $owner = (int) $wpdb->get_var($wpdb->prepare("SELECT user_id FROM " . smsp1_table('groups') . " WHERE id=%d", $gid));
            if ($owner !== $uid) return new WP_Error('forbidden', 'گروه متعلق به شما نیست', ['status' => 403]);

            $items = $req->get_param('contacts');
            if (!$items) { // single mode {name, mobile}
                $items = [['name' => $req->get_param('name'), 'mobile' => $req->get_param('mobile')]];
            }
            $inserted = 0; $skipped = 0;
            foreach ((array) $items as $c) {
                $name = sanitize_text_field($c['name'] ?? '');
                $mobile = smsp1_valid_mobile($c['mobile'] ?? '');
                if (!$mobile) { $skipped++; continue; }
                $r = $wpdb->query($wpdb->prepare(
                    "INSERT IGNORE INTO " . smsp1_table('contacts') . " (user_id, group_id, name, mobile) VALUES (%d,%d,%s,%s)",
                    $uid, $gid, $name, $mobile
                ));
                if ($r) $inserted++; else $skipped++;
            }
            return ['inserted' => $inserted, 'skipped' => $skipped];
        })],
    ]);

    register_rest_route($ns, '/queue', [
        'methods' => 'GET', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            return $wpdb->get_results($wpdb->prepare("SELECT id, title, body, status, total, sent, failed, created_at FROM " . smsp1_table('campaigns') . " WHERE user_id=%d ORDER BY id DESC LIMIT 100", $uid), ARRAY_A);
        }),
    ]);

    register_rest_route($ns, '/queue/counts', [
        'methods' => 'GET', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            $q = smsp1_table('queue');
            $pending = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $q WHERE user_id=%d AND status IN ('pending','sending')", $uid));
            $sent = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $q WHERE user_id=%d AND status='sent' AND DATE(updated_at)=CURDATE()", $uid));
            return ['pending' => $pending, 'sent' => $sent];
        }),
    ]);

    register_rest_route($ns, '/build-queue', [
        'methods' => 'POST', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            $gid = (int) $req->get_param('group_id');
            $body = (string) $req->get_param('manual_body');
            if ($gid <= 0 || !$body) return new WP_Error('bad', 'group_id و manual_body لازم است', ['status' => 400]);
            // tenant isolation: group must belong to the caller (IDOR guard)
            $owner = (int) $wpdb->get_var($wpdb->prepare("SELECT user_id FROM " . smsp1_table('groups') . " WHERE id=%d", $gid));
            if ($owner !== $uid) return new WP_Error('forbidden', 'گروه متعلق به شما نیست', ['status' => 403]);
            $contacts = $wpdb->get_results($wpdb->prepare("SELECT name, mobile FROM " . smsp1_table('contacts') . " WHERE user_id=%d AND group_id=%d", $uid, $gid), ARRAY_A);
            if (!$contacts) return new WP_Error('empty', 'مخاطبی در این گروه نیست', ['status' => 400]);
            $gname = $wpdb->get_var($wpdb->prepare("SELECT name FROM " . smsp1_table('groups') . " WHERE id=%d", $gid));
            $wpdb->insert(smsp1_table('campaigns'), [
                'user_id' => $uid, 'group_id' => $gid,
                'title' => $gname ?: ('کمپین #' . $gid),
                'body' => $body, 'total' => count($contacts), 'status' => 'pending',
            ]);
            $cid = (int) $wpdb->insert_id;
            foreach ($contacts as $c) {
                $wpdb->insert(smsp1_table('queue'), [
                    'user_id' => $uid, 'campaign_id' => $cid,
                    'receiver' => $c['mobile'],
                    'body' => smsp1_render_name($body, $c['name']),
                    'status' => 'pending',
                ]);
            }
            return ['campaign_id' => $cid, 'queued' => count($contacts)];
        }),
    ]);

    register_rest_route($ns, '/queue/fetch', [
        'methods' => 'POST', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            $limit = min(20, max(1, (int) ($req->get_param('limit') ?: 5)));
            $q = smsp1_table('queue');
            // رفع گیر: هر پیامی که در وضعیت 'sending' جا مانده باشد (اپ یا سرویس در میانه‌ی کار
            // کشته شده) بعد از ۵ دقیقه به 'pending' برمی‌گردد؛ وگرنه آن پیام‌ها تا ابد گم می‌شدند
            // و شمارنده‌ی «در انتظار» هم هیچ‌وقت پایین نمی‌آمد.
            $wpdb->query($wpdb->prepare(
                "UPDATE $q SET status='pending' WHERE user_id=%d AND status='sending' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)",
                $uid
            ));
            $rows = $wpdb->get_results($wpdb->prepare("SELECT id, receiver, body FROM $q WHERE user_id=%d AND status='pending' ORDER BY id ASC LIMIT %d", $uid, $limit), ARRAY_A);
            if ($rows) {
                $ids = array_map('intval', array_column($rows, 'id'));
                $in = implode(',', $ids);
                $wpdb->query("UPDATE $q SET status='sending' WHERE id IN ($in)");
            }
            return $rows ?: [];
        }),
    ]);

    register_rest_route($ns, '/queue/update', [
        'methods' => 'POST', 'permission_callback' => $perm, 'callback' => $authed(function ($req, $uid) {
            global $wpdb;
            $id = (int) $req->get_param('id');
            $st = $req->get_param('status') === 'failed' ? 'failed' : 'sent';
            $q = smsp1_table('queue');
            $row = $wpdb->get_row($wpdb->prepare("SELECT campaign_id, status FROM $q WHERE id=%d AND user_id=%d", $id, $uid), ARRAY_A);
            if (!$row) return new WP_Error('notfound', 'پیام یافت نشد', ['status' => 404]);
            $cid = (int) $row['campaign_id'];
            // اگر وضعیت قبلاً نهایی شده، دوباره شمارش نکن (جلوگیری از آمار اشتباه کمپین)
            if (in_array($row['status'], ['sent', 'failed'], true)) return ['ok' => true, 'already' => $row['status']];
            $wpdb->query($wpdb->prepare("UPDATE $q SET status=%s WHERE id=%d", $st, $id));
            $c = smsp1_table('campaigns');
            if ($st === 'sent') $wpdb->query($wpdb->prepare("UPDATE $c SET sent=sent+1 WHERE id=%d", $cid));
            else $wpdb->query($wpdb->prepare("UPDATE $c SET failed=failed+1 WHERE id=%d", $cid));
            $tot = $wpdb->get_row($wpdb->prepare("SELECT total, sent, failed FROM $c WHERE id=%d", $cid), ARRAY_A);
            if ($tot && ((int) $tot['sent'] + (int) $tot['failed']) >= (int) $tot['total']) {
                $wpdb->query($wpdb->prepare("UPDATE $c SET status='sent' WHERE id=%d", $cid));
            } else {
                $wpdb->query($wpdb->prepare("UPDATE $c SET status='sending' WHERE id=%d", $cid));
            }
            return ['ok' => true];
        }),
    ]);
});

// ---------- admin UI ----------
require_once __DIR__ . '/includes/admin.php';

// ---------- HTTPS notice ----------
add_action('admin_notices', function () {
    if (!is_ssl() && current_user_can('manage_options') && isset($_GET['page']) && $_GET['page'] === 'smsp1-panel') {
        echo '<div class="notice notice-warning"><p>هشدار: سایت روی HTTPS نیست. توکن از طریق هدر Authorization فرستاده می‌شود — حتماً SSL فعال کن.</p></div>';
    }
});

// ---------- auto-update from GitHub releases ----------
// نسخه از نام asset استخراج می‌شود: mahdinikzad-sms-gateway-<version>.zip
// (قبلاً tag ریلیز خوانده می‌شد و چون تگ‌ها به شکل v7.<run_id> ساخته می‌شوند،
//  مقایسه‌ی نسخه بی‌معنی و آپدیت اشتباه/خراب می‌شد.)
add_filter('pre_set_site_transient_update_plugins', function ($transient) {
    if (empty($transient->checked)) return $transient;

    $remote = get_transient('smsp1_gh_version');
    if ($remote === false) {
        $res = wp_remote_get('https://api.github.com/repos/' . SMSP1_GITHUB_REPO . '/releases?per_page=20', [
            'headers' => [
                'User-Agent' => 'mahdinikzad-sms-gateway',
                'Accept' => 'application/vnd.github+json',
            ],
            'timeout' => 12,
        ]);
        $found = null;
        if (!is_wp_error($res) && wp_remote_retrieve_response_code($res) === 200) {
            $list = json_decode(wp_remote_retrieve_body($res), true);
            foreach ((array) $list as $rel) {
                if (!empty($rel['draft'])) continue;
                foreach ((array) ($rel['assets'] ?? []) as $a) {
                    $asset = (string) ($a['name'] ?? '');
                    if (preg_match('/^mahdinikzad-sms-gateway-(\d+\.\d+\.\d+)\.zip$/i', $asset, $m)) {
                        $found = [
                            'ver' => $m[1],
                            'url' => (string) ($rel['html_url'] ?? ''),
                            'package' => (string) ($a['browser_download_url'] ?? ''),
                        ];
                        break 2;
                    }
                }
            }
        }
        set_transient('smsp1_gh_version', $found ?: 'none', 6 * HOUR_IN_SECONDS);
        $remote = $found ?: false;
    }

    if (is_array($remote) && !empty($remote['ver']) && !empty($remote['package'])
        && version_compare($remote['ver'], SMSP1_VERSION, '>')) {
        $transient->response[SMSP1_SLUG] = (object) [
            'slug' => 'mahdinikzad-sms-gateway',
            'plugin' => SMSP1_SLUG,
            'new_version' => $remote['ver'],
            'url' => $remote['url'] ?: ('https://github.com/' . SMSP1_GITHUB_REPO),
            'package' => $remote['package'],
        ];
    }
    return $transient;
});
