<?php
if (!defined('ABSPATH')) exit;

add_action('admin_menu', function () {
    add_menu_page(
        'پنل پیامکی',
        'پنل پیامکی',
        'manage_options',
        'smsp1-panel',
        'smsp1_render_admin_page',
        'dashicons-email-alt',
        26
    );
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
        // revoke current token when deactivating, forces re-login
        if ($active === '0') delete_user_meta($user_id, SMSP1_TOKEN_META);
    }
    wp_safe_redirect(admin_url('admin.php?page=smsp1-panel&updated=1'));
    exit;
}

function smsp1_render_admin_page() {
    if (!current_user_can('manage_options')) return;
    global $wpdb;

    $users = get_users(['fields' => ['ID', 'user_login', 'display_name']]);
    $groups_table = $wpdb->prefix . 'smsp1_groups';
    $queue_table = $wpdb->prefix . 'smsp1_queue';
    ?>
    <style>
        .smsp1-wrap{max-width:720px;margin-top:10px}
        .smsp1-card{background:#fff;border:1px solid #e0e0e0;border-radius:10px;padding:16px;margin-bottom:14px}
        .smsp1-row{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px}
        .smsp1-badge{padding:3px 10px;border-radius:12px;font-size:12px;font-weight:600}
        .smsp1-active{background:#d4f7dc;color:#0a7d2c}
        .smsp1-inactive{background:#fde2e2;color:#c0392b}
        .smsp1-card input[type=date]{padding:6px;border-radius:6px;border:1px solid #ccc}
        @media(max-width:600px){.smsp1-wrap{padding:0 8px}}
    </style>
    <div class="wrap smsp1-wrap">
        <h1>پنل مدیریت پیامکی</h1>
        <?php if (!empty($_GET['updated'])): ?>
            <div class="notice notice-success"><p>ذخیره شد.</p></div>
        <?php endif; ?>

        <?php foreach ($users as $u):
            $active = get_user_meta($u->ID, SMSP1_LICENSE_ACTIVE_META, true) === '1';
            $expires = get_user_meta($u->ID, SMSP1_LICENSE_EXPIRES_META, true);
            $group_count = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $groups_table WHERE user_id=%d", $u->ID));
            $pending = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $queue_table WHERE user_id=%d AND status IN ('pending','sending')", $u->ID));
            $sent_today = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $queue_table WHERE user_id=%d AND status='sent' AND DATE(updated_at)=CURDATE()", $u->ID));
        ?>
        <div class="smsp1-card">
            <form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
                <?php wp_nonce_field('smsp1_save_license'); ?>
                <input type="hidden" name="action" value="smsp1_save_license">
                <input type="hidden" name="user_id" value="<?php echo esc_attr($u->ID); ?>">
                <div class="smsp1-row">
                    <strong><?php echo esc_html($u->display_name ?: $u->user_login); ?></strong>
                    <span class="smsp1-badge <?php echo $active ? 'smsp1-active' : 'smsp1-inactive'; ?>">
                        <?php echo $active ? 'فعال' : 'غیرفعال'; ?>
                    </span>
                </div>
                <p style="color:#666;font-size:13px;margin:6px 0">
                    گروه‌ها: <?php echo $group_count; ?> &nbsp;|&nbsp;
                    در صف: <?php echo $pending; ?> &nbsp;|&nbsp;
                    امروز ارسال شد: <?php echo $sent_today; ?>
                </p>
                <div class="smsp1-row">
                    <label><input type="checkbox" name="active" <?php checked($active); ?>> لایسنس فعال باشد</label>
                    <label>انقضا: <input type="date" name="expires" value="<?php echo esc_attr($expires); ?>"></label>
                </div>
                <p style="margin-top:10px">
                    <button type="submit" class="button button-primary">ذخیره</button>
                </p>
            </form>
        </div>
        <?php endforeach; ?>
    </div>
    <?php
}
