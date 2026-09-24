<?php
if (!defined('ABSPATH')) exit;

// Admin actions
add_action('admin_menu', function () {
    add_menu_page('پنل پیامکی', 'پنل پیامکی', 'manage_options', 'smsp1-panel', 'smsp1_render_admin_page', 'dashicons-email-alt', 26);
});

add_action('admin_post_smsp1_save_license', 'smsp1_handle_save_license');
function smsp1_handle_save_license() {
    if (!current_user_can('manage_options')) wp_die('دسترسی ندارید');
    check_admin_referer('smsp1_save_license');
    $user_id = (int) ($_POST['user_id'] ?? 0);
    $active = isset($_POST['active']) ? '1' : '0';
    $expires = sanitize_text_field($_POST['expires'] ?? '');
    if ($user_id > 0) {
        update_user_meta($user_id, SMSP1_LICENSE_ACTIVE_META, $active);
        update_user_meta($user_id, SMSP1_LICENSE_EXPIRES_META, $expires);
        if ($active === '0') delete_user_meta($user_id, SMSP1_TOKEN_META);
    }
    wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=licenses&updated=1'));
    exit;
}

add_action('admin_post_smsp1_group_add', function () {
    if (!current_user_can('manage_options')) wp_die('دسترسی ندارید');
    check_admin_referer('smsp1_group_add');
    global $wpdb;
    $user_id = (int) ($_POST['user_id'] ?? 0);
    $name = sanitize_text_field($_POST['name'] ?? '');
    if ($user_id > 0 && $name) $wpdb->insert(smsp1_table('groups'), ['user_id' => $user_id, 'name' => $name]);
    wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=groups&updated=1'));
    exit;
});

add_action('admin_post_smsp1_unstick', function () {
    if (!current_user_can('manage_options')) wp_die('دسترسی ندارید');
    check_admin_referer('smsp1_unstick');
    global $wpdb;
    $q = smsp1_table('queue');
    $n = (int) $wpdb->query("UPDATE $q SET status='pending' WHERE status='sending' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)");
    wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=campaigns&unstuck=' . $n));
    exit;
});

add_action('admin_post_smsp1_group_del', function () {
    if (!current_user_can('manage_options')) wp_die('دسترسی ندارید');
    check_admin_referer('smsp1_group_del');
    global $wpdb;
    $gid = (int) ($_POST['group_id'] ?? 0);
    if ($gid > 0) {
        $wpdb->delete(smsp1_table('contacts'), ['group_id' => $gid]);
        $wpdb->delete(smsp1_table('groups'), ['id' => $gid]);
    }
    wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&tab=groups&updated=1'));
    exit;
});

function smsp1_admin_tab() {
    $t = $_GET['tab'] ?? 'dashboard';
    return in_array($t, ['dashboard', 'groups', 'contacts', 'campaigns', 'licenses'], true) ? $t : 'dashboard';
}

function smsp1_render_admin_page() {
    if (!current_user_can('manage_options')) return;
    global $wpdb;
    $tab = smsp1_admin_tab();
    $gT = smsp1_table('groups'); $cT = smsp1_table('contacts');
    $qT = smsp1_table('queue'); $mT = smsp1_table('campaigns');

    $total_users = count_users()['total_users'];
    $total_groups = (int) $wpdb->get_var("SELECT COUNT(*) FROM $gT");
    $total_contacts = (int) $wpdb->get_var("SELECT COUNT(*) FROM $cT");
    $pending = (int) $wpdb->get_var("SELECT COUNT(*) FROM $qT WHERE status IN ('pending','sending')");
    $sent_today = (int) $wpdb->get_var("SELECT COUNT(*) FROM $qT WHERE status='sent' AND DATE(updated_at)=CURDATE()");
    $sent_total = (int) $wpdb->get_var("SELECT COUNT(*) FROM $qT WHERE status='sent'");
    $failed_total = (int) $wpdb->get_var("SELECT COUNT(*) FROM $qT WHERE status='failed'");
    $ssl_ok = is_ssl();
    ?>
    <style>
        .smsp1-wrap{max-width:1100px;margin:16px 0;font-family:Tahoma,Arial}
        .smsp1-tabs{margin:12px 0;display:flex;gap:6px;flex-wrap:wrap}
        .smsp1-tabs a{padding:8px 16px;border-radius:20px;background:#fff;border:1px solid #ddd;text-decoration:none;color:#333;font-weight:600;font-size:13px}
        .smsp1-tabs a.active{background:#128C7E;color:#fff;border-color:#128C7E}
        .smsp1-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin:14px 0}
        .smsp1-stat{background:#fff;border:1px solid #e5e5e5;border-radius:14px;padding:16px;text-align:center}
        .smsp1-stat b{font-size:26px;display:block;color:#075E54}
        .smsp1-stat span{font-size:12px;color:#666}
        .smsp1-card{background:#fff;border:1px solid #e5e5e5;border-radius:14px;padding:16px;margin-bottom:14px}
        .smsp1-table{width:100%;border-collapse:collapse;font-size:13px}
        .smsp1-table th,.smsp1-table td{padding:10px 8px;border-bottom:1px solid #f0f0f0;text-align:right}
        .smsp1-table th{background:#F0F2F5;color:#075E54}
        .smsp1-badge{padding:3px 12px;border-radius:12px;font-size:12px;font-weight:700}
        .smsp1-ok{background:#d4f7dc;color:#0a7d2c}.smsp1-bad{background:#fde2e2;color:#c0392b}
        .smsp1-warn{background:#fef3cd;color:#926100}.smsp1-info{background:#e3f2fd;color:#0b5fa5}
        .smsp1-bar{height:8px;background:#eee;border-radius:6px;overflow:hidden;min-width:90px}
        .smsp1-bar i{display:block;height:100%;background:linear-gradient(90deg,#25D366,#128C7E)}
        .smsp1-form{display:flex;gap:8px;flex-wrap:wrap;align-items:end}
        .smsp1-form input,.smsp1-form select{padding:8px 10px;border:1px solid #ccc;border-radius:8px}
        .smsp1-alert{border-radius:10px;padding:12px 16px;margin-bottom:12px;font-size:13px}
        @media(max-width:700px){.smsp1-table th:nth-child(n+4),.smsp1-table td:nth-child(n+4){display:none}}
    </style>
    <div class="wrap smsp1-wrap">
        <h1>پنل پیامکی <small style="font-size:12px;color:#888">v<?php echo esc_html(SMSP1_VERSION); ?></small></h1>
        <?php if (!$ssl_ok): ?><div class="smsp1-alert" style="background:#fde2e2">هشدار: SSL فعال نیست — توکن فقط روی HTTPS امن است.</div><?php endif; ?>
        <?php if (!empty($_GET['updated'])): ?><div class="notice notice-success"><p>ذخیره شد.</p></div><?php endif; ?>
        <div class="smsp1-tabs">
            <?php foreach (['dashboard' => 'داشبورد', 'groups' => 'گروه‌ها', 'contacts' => 'مخاطبین', 'campaigns' => 'کمپین‌ها و صف', 'licenses' => 'لایسنس‌ها'] as $k => $l): ?>
                <a class="<?php echo $tab === $k ? 'active' : ''; ?>" href="<?php echo esc_url(admin_url('admin.php?page=smsp1-panel&tab=' . $k)); ?>"><?php echo esc_html($l); ?></a>
            <?php endforeach; ?>
        </div>

        <?php if ($tab === 'dashboard'): ?>
            <div class="smsp1-grid">
                <div class="smsp1-stat"><b><?php echo number_format($total_contacts); ?></b><span>مخاطب</span></div>
                <div class="smsp1-stat"><b><?php echo number_format($total_groups); ?></b><span>گروه</span></div>
                <div class="smsp1-stat"><b><?php echo number_format($pending); ?></b><span>در صف</span></div>
                <div class="smsp1-stat"><b><?php echo number_format($sent_today); ?></b><span>ارسال امروز</span></div>
                <div class="smsp1-stat"><b><?php echo number_format($sent_total); ?></b><span>کل موفق</span></div>
                <div class="smsp1-stat"><b><?php echo number_format($failed_total); ?></b><span>ناموفق</span></div>
            </div>
            <div class="smsp1-card">
                <h3>وضعیت سامانه</h3>
                <p>کاربران وردپرس: <b><?php echo (int) $total_users; ?></b> | SSL: <?php echo $ssl_ok ? '<span class="smsp1-badge smsp1-ok">فعال</span>' : '<span class="smsp1-badge smsp1-bad">غیرفعال</span>'; ?> | نسخه افزونه: <b><?php echo esc_html(SMSP1_VERSION); ?></b></p>
                <p style="color:#666;font-size:13px">مسیر API: <code>/wp-json/smsp1/v1/</code> — اپ اندروید با همین API کار می‌کند. راهنمای کامل در فایل readme داخل مخزن گیت‌هاب است.</p>
            </div>
            <div class="smsp1-card">
                <h3>آخرین کمپین‌ها</h3>
                <?php $rows = $wpdb->get_results("SELECT c.*, u.display_name FROM $mT c LEFT JOIN $wpdb->users u ON u.ID=c.user_id ORDER BY c.id DESC LIMIT 8", ARRAY_A); ?>
                <?php if ($rows): ?>
                <table class="smsp1-table"><tr><th>کمپین</th><th>کاربر</th><th>پیشرفت</th><th>وضعیت</th></tr>
                <?php foreach ($rows as $r): $pct = $r['total'] ? round(100 * ($r['sent'] + $r['failed']) / max(1, $r['total'])) : 0; ?>
                    <tr><td><?php echo esc_html($r['title']); ?></td><td><?php echo esc_html($r['display_name'] ?? ('#' . $r['user_id'])); ?></td>
                    <td><div class="smsp1-bar"><i style="width:<?php echo (int) $pct; ?>%"></i></div><small><?php echo (int) $r['sent']; ?>/<?php echo (int) $r['total']; ?></small></td>
                    <td><span class="smsp1-badge <?php echo $r['status'] === 'sent' ? 'smsp1-ok' : 'smsp1-warn'; ?>"><?php echo esc_html($r['status']); ?></span></td></tr>
                <?php endforeach; ?></table>
                <?php else: ?><p style="color:#888">هنوز کمپینی ساخته نشده — از داخل اپ، «ارسال جدید» را بزن.</p><?php endif; ?>
            </div>
        <?php endif; ?>

        <?php if ($tab === 'groups'): ?>
            <div class="smsp1-card"><h3>ساخت گروه</h3>
                <form class="smsp1-form" method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
                    <?php wp_nonce_field('smsp1_group_add'); ?>
                    <input type="hidden" name="action" value="smsp1_group_add">
                    <label>کاربر<br><select name="user_id"><?php foreach (get_users(['fields' => ['ID', 'user_login', 'display_name']]) as $u): ?><option value="<?php echo (int) $u->ID; ?>"><?php echo esc_html($u->display_name ?: $u->user_login); ?></option><?php endforeach; ?></select></label>
                    <label>نام گروه<br><input name="name" required placeholder="مثلاً مشتریان تهران"></label>
                    <button class="button button-primary" type="submit">ساخت</button>
                </form>
            </div>
            <div class="smsp1-card"><h3>همه گروه‌ها</h3>
                <table class="smsp1-table"><tr><th>گروه</th><th>کاربر</th><th>مخاطب</th><th>حذف</th></tr>
                <?php foreach ($wpdb->get_results("SELECT g.*, u.display_name, (SELECT COUNT(*) FROM $cT WHERE group_id=g.id) AS cc FROM $gT g LEFT JOIN $wpdb->users u ON u.ID=g.user_id ORDER BY g.id DESC LIMIT 200", ARRAY_A) as $g): ?>
                    <tr><td><b><?php echo esc_html($g['name']); ?></b> <small>#<?php echo (int) $g['id']; ?></small></td>
                    <td><?php echo esc_html($g['display_name'] ?? ('#' . $g['user_id'])); ?></td>
                    <td><?php echo number_format($g['cc']); ?></td>
                    <td><form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>" onsubmit="return confirm('حذف شود؟ مخاطبینش هم پاک می‌شوند.')"><?php wp_nonce_field('smsp1_group_del'); ?><input type="hidden" name="action" value="smsp1_group_del"><input type="hidden" name="group_id" value="<?php echo (int) $g['id']; ?>"><button class="button button-link-delete" type="submit">حذف</button></form></td></tr>
                <?php endforeach; ?></table>
            </div>
        <?php endif; ?>

        <?php if ($tab === 'contacts'): ?>
            <div class="smsp1-card">
                <form class="smsp1-form" method="get">
                    <input type="hidden" name="page" value="smsp1-panel"><input type="hidden" name="tab" value="contacts">
                    <label>جستجو<br><input name="s" value="<?php echo esc_attr($_GET['s'] ?? ''); ?>" placeholder="نام یا موبایل"></label>
                    <button class="button" type="submit">فیلتر</button>
                </form><br>
                <table class="smsp1-table"><tr><th>نام</th><th>موبایل</th><th>گروه</th><th>کاربر</th></tr>
                <?php
                $s = trim($_GET['s'] ?? '');
                $where = '1=1';
                if ($s) $where .= $wpdb->prepare(' AND (c.name LIKE %s OR c.mobile LIKE %s)', '%' . $wpdb->esc_like($s) . '%', '%' . $wpdb->esc_like($s) . '%');
                foreach ($wpdb->get_results("SELECT c.*, g.name AS gname, u.display_name FROM $cT c LEFT JOIN $gT g ON g.id=c.group_id LEFT JOIN $wpdb->users u ON u.ID=c.user_id WHERE $where ORDER BY c.id DESC LIMIT 300", ARRAY_A) as $c): ?>
                    <tr><td><?php echo esc_html($c['name'] ?: '—'); ?></td><td dir="ltr"><?php echo esc_html($c['mobile']); ?></td><td><?php echo esc_html($c['gname'] ?? ('#' . $c['group_id'])); ?></td><td><?php echo esc_html($c['display_name'] ?? ('#' . $c['user_id'])); ?></td></tr>
                <?php endforeach; ?></table>
                <p style="color:#888;font-size:12px">نمایش تا ۳۰۰ رکورد — افزودن مخاطب از داخل اپ (مخاطبین گوشی / CSV) انجام می‌شود.</p>
            </div>
        <?php endif; ?>

        <?php if ($tab === 'campaigns'): ?>
            <?php if (isset($_GET['unstuck'])): ?><div class="notice notice-success"><p>تعداد <?php echo (int) $_GET['unstuck']; ?> پیام گیرافتاده به صف برگشت.</p></div><?php endif; ?>
            <div class="smsp1-card"><h3>کمپین‌ها و صف ارسال</h3>
                <form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>" style="margin-bottom:12px">
                    <?php wp_nonce_field('smsp1_unstick'); ?>
                    <input type="hidden" name="action" value="smsp1_unstick">
                    <button class="button" type="submit">آزادسازی پیام‌های گیرافتاده</button>
                    <small style="color:#888;margin-right:8px">پیام‌هایی که بیش از ۵ دقیقه در وضعیت sending مانده‌اند به صف برمی‌گردند (خودکار هم انجام می‌شود).</small>
                </form>
                <table class="smsp1-table"><tr><th>کمپین</th><th>متن</th><th>پیشرفت</th><th>وضعیت</th><th>تاریخ</th></tr>
                <?php foreach ($wpdb->get_results("SELECT * FROM $mT ORDER BY id DESC LIMIT 100", ARRAY_A) as $r): $pct = $r['total'] ? round(100 * ($r['sent'] + $r['failed']) / max(1, $r['total'])) : 0; ?>
                    <tr><td><b><?php echo esc_html($r['title']); ?></b><br><small>#<?php echo (int) $r['id']; ?> کاربر #<?php echo (int) $r['user_id']; ?></small></td>
                    <td><?php echo esc_html(mb_substr($r['body'], 0, 60)); ?>…</td>
                    <td><div class="smsp1-bar"><i style="width:<?php echo (int) $pct; ?>%"></i></div><small>موفق <?php echo (int) $r['sent']; ?> • ناموفق <?php echo (int) $r['failed']; ?> • کل <?php echo (int) $r['total']; ?></small></td>
                    <td><span class="smsp1-badge <?php echo $r['status'] === 'sent' ? 'smsp1-ok' : 'smsp1-warn'; ?>"><?php echo esc_html($r['status']); ?></span></td>
                    <td><small><?php echo esc_html($r['created_at']); ?></small></td></tr>
                <?php endforeach; ?></table>
            </div>
        <?php endif; ?>

        <?php if ($tab === 'licenses'): ?>
            <?php foreach (get_users(['fields' => ['ID', 'user_login', 'display_name']]) as $u):
                $active = get_user_meta($u->ID, SMSP1_LICENSE_ACTIVE_META, true) === '1';
                $expires = get_user_meta($u->ID, SMSP1_LICENSE_EXPIRES_META, true);
                $gc = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $gT WHERE user_id=%d", $u->ID));
                $cc = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $cT WHERE user_id=%d", $u->ID));
                $pen = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $qT WHERE user_id=%d AND status IN ('pending','sending')", $u->ID));
                $tod = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $qT WHERE user_id=%d AND status='sent' AND DATE(updated_at)=CURDATE()", $u->ID));
            ?>
            <div class="smsp1-card">
                <form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
                    <?php wp_nonce_field('smsp1_save_license'); ?>
                    <input type="hidden" name="action" value="smsp1_save_license">
                    <input type="hidden" name="user_id" value="<?php echo (int) $u->ID; ?>">
                    <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
                        <strong><?php echo esc_html($u->display_name ?: $u->user_login); ?></strong>
                        <span class="smsp1-badge <?php echo $active ? 'smsp1-ok' : 'smsp1-bad'; ?>"><?php echo $active ? 'فعال' : 'غیرفعال'; ?></span>
                    </div>
                    <p style="color:#666;font-size:13px">گروه‌ها: <?php echo $gc; ?> | مخاطبین: <?php echo $cc; ?> | در صف: <?php echo $pen; ?> | امروز: <?php echo $tod; ?></p>
                    <div class="smsp1-form">
                        <label><input type="checkbox" name="active" <?php checked($active); ?>> لایسنس فعال باشد</label>
                        <label>انقضا: <input type="date" name="expires" value="<?php echo esc_attr($expires); ?>"></label>
                        <button type="submit" class="button button-primary">ذخیره</button>
                    </div>
                </form>
            </div>
            <?php endforeach; ?>
        <?php endif; ?>
    </div>
    <?php
}
