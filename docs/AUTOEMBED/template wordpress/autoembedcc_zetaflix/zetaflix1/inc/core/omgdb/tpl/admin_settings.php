<div class="wrap omegadb-settings">
    <nav class="omgdb-menu">
        <ul id="omgdb-nav-settings" class="items">
            <li id="settab-general" data-tab="general" class="nav-tab nav-tab-active"><?php _z('General'); ?></li>
            <li id="settab-featimg" data-tab="featimg" class="nav-tab"><?php _z('Featured Image'); ?></li>
            <li id="settab-titles" data-tab="titles" class="nav-tab"><?php _z('Titles and Content'); ?></li>
            <li id="settab-updater" data-tab="updater" class="nav-tab"><?php _z('Meta Updater'); ?></li>
            <li id="settab-requests" data-tab="requests" class="nav-tab"><?php _z('Requests'); ?></li>
            <li id="settab-advanced" data-tab="advanced" class="nav-tab"><?php _z('Advanced'); ?></li>
        </ul>
    </nav>
    <?php if(empty($this->get_option('omegadb'))){ ?>
    <div class="notice notice-info is-dismissible">
        <p><?php _z('Register on our platform to obtain an API key'); ?> <a href="<?php echo OMEGADB_HOME; ?>" target="_blank"><strong><?php _z('Click here'); ?></strong></a></p>
        <button type="button" class="notice-dismiss">
            <span class="screen-reader-text"></span>
        </button>
    </div>
    <?php } ?>
    <?php if(empty($this->get_option('themoviedb'))){ ?>
    <div class="notice notice-info is-dismissible">
        <p><?php _z('Get API Key (v3 auth) for Themoviedb'); ?> <a href="https://www.themoviedb.org/settings/api" target="_blank"><strong><?php _z('Click here'); ?></strong></a></p>
        <button type="button" class="notice-dismiss">
            <span class="screen-reader-text"></span>
        </button>
    </div>
    <?php } ?>
    <form id="omegadb-settings">
        <div id="dbmv-setting-general" class="tab-content on">
            <?php require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/form_setting_general.php'); ?>
        </div>
        <div id="dbmv-setting-featimg" class="tab-content">
            <?php require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/form_setting_featimg.php'); ?>
        </div>
        <div id="dbmv-setting-titles" class="tab-content">
            <?php require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/form_setting_titles.php'); ?>
        </div>
        <div id="dbmv-setting-updater" class="tab-content">
            <?php require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/form_setting_updater.php'); ?>
        </div>
        <div id="dbmv-setting-requests" class="tab-content">
            <?php require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/form_setting_requests.php'); ?>
        </div>
        <div id="dbmv-setting-advanced" class="tab-content">
            <?php require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/form_setting_advanced.php'); ?>
        </div>
        <hr>
        <p>
            <input type="hidden" name="action" value="omegadb_savesetting">
            <input type="hidden" name="cnonce" value="<?php echo wp_create_nonce('omegadb-save-settings'); ?>">
            <input type="submit" class="button button-primary" value="<?php _z('Save Changes'); ?>" id="omgdbbtn-savesettings">
            <a href="<?php echo get_admin_url(get_current_blog_id(),'admin-ajax.php?action=omegadb_clean_cache'); ?>" class="button button-secundary"><?php _z('Delete cache'); ?></a>
            <span id="omgdbssrespnc"></span>
        </p>
    </form>
</div>
