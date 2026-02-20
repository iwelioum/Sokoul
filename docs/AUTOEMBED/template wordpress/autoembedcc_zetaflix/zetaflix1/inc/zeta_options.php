<?php if(!defined('ABSPATH')) die;
/**
 * ZetaFlix Options for Codestar Framework
 * @author Zetathemes and Omgdb Team
 * @since 1.0.0
 * @version 2.1
 */

/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createOptions(ZETA_OPTIONS,
    array(
        'framework_title'    => 'ZetaFlix <small>Options</small>',
        'menu_title'         => sprintf( __z('ZetaFlix Options'), ZETA_THEME),
        'menu_slug'          => 'zetaflix',
        'menu_type'          => 'submenu',
        'menu_parent'        => 'themes.php',
        'theme'              => 'light',
        'ajax_save'          => true,
        'show_reset_all'     => false,
        'show_reset_section' => false,
        'show_footer'        => true,
        'footer_after'       => '',
        'footer_credit'      => 'Thank you for creating with <a href="https://bit.ly/3sVK4Gc" target="_blank"><strong>Zetathemes</strong></a> and '.ucfirst(ZETA_THEME_SLUG).' v'.ZETA_VERSION,
    )
);

// All ZetaFlix Options for CSF
require_once get_parent_theme_file_path('/inc/csf/options.main_settings.php');
require_once get_parent_theme_file_path('/inc/csf/options.customize.php');
require_once get_parent_theme_file_path('/inc/csf/options.posters.php');
require_once get_parent_theme_file_path('/inc/csf/options.avatars.php');
require_once get_parent_theme_file_path('/inc/csf/options.pages.php');
require_once get_parent_theme_file_path('/inc/csf/options.watch_settings.php');
require_once get_parent_theme_file_path('/inc/csf/options.comments.php');
require_once get_parent_theme_file_path('/inc/csf/options.links_module.php');
require_once get_parent_theme_file_path('/inc/csf/options.video_player.php');
require_once get_parent_theme_file_path('/inc/csf/options.wp_mail.php');
require_once get_parent_theme_file_path('/inc/csf/options.main_slider.php');
require_once get_parent_theme_file_path('/inc/csf/options.top_imdb.php');
require_once get_parent_theme_file_path('/inc/csf/options.module_movies.php');
require_once get_parent_theme_file_path('/inc/csf/options.module_tvshows.php');
require_once get_parent_theme_file_path('/inc/csf/options.module_seasons.php');
require_once get_parent_theme_file_path('/inc/csf/options.module_episodes.php');
require_once get_parent_theme_file_path('/inc/csf/options.more.php');
