<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

class ZetaNotices{

    public function __construct(){
        if(is_admin()){
            $omegadb = get_option('_omegadb_settings');
          	$zetawidgets = get_option('zetaflix_widgets');
            $zetapages = get_option('zetaflix_pages');
            $database = get_option('zetaflix_database');
            $updateml = get_option('zetaflix_update_linksmodule');
            $licesenk = get_option('zetaflix_license_key');
            $licenses = get_option('zetaflix_license_key_status');
            $currpage = zeta_isset($_GET,'page');

            if(version_compare(phpversion(), ZETA_PHP_REQUIRE, '<')){
                add_action('admin_notices',array($this,'php_require'));
            }

            if($licenses !== 'valid'){
                add_action('admin_notices', array($this,'activate_license'));
            }elseif(!$zetapages && $currpage != 'zetaflix-database'){
                add_action('admin_notices',array($this,'generate_pages'));
            }elseif(!$zetawidgets && $currpage != 'zetaflix-database'){
                add_action('admin_notices', array($this,'generate_widgets'));
            }elseif(empty(zeta_isset($omegadb,'omegadb')) && $currpage != 'omgdb-settings'){
                add_action('admin_notices',array($this,'activate_omegadb'));
            }elseif(!$updateml && $currpage != 'zetaflix-database'){
                add_action('admin_notices', array($this,'update_linksmodule'));
            }elseif($updateml && $database !== ZETA_VERSION_DB && $currpage != 'zetaflix-database'){
                add_action('admin_notices', array($this,'update_database'));
            }
        }
    }

    public function php_require(){
        $out  = '<div class="notice notice-warning is-dismissible"><p>';
        $out .= sprintf( __z('ZetaFlix requires <strong>PHP %1$s+</strong>. Please ask your webhost to upgrade to at least PHP %1$s. Recommended: <strong>PHP 7.2</strong>'), ZETA_PHP_REQUIRE);
        $out .= '</p></div>';
        echo $out;
    }

    public function activate_license(){
        $out  = '<div class="notice notice-info is-dismissible"><p>';
    	$out .= '<span class="dashicons dashicons-warning" style="color: #00a0d2"></span> ';
        $out .= __z('Invalid license, it is possible that some of the options may not work correctly'). ', '.'<a href="'. admin_url('themes.php?page=zetaflix-license').'"><strong>'. __z('here'). '</strong></a>';
        $out .= '</p></div>';
        echo $out;
    }

    public function update_database(){
        $out = '<div class="notice notice-info is-dismissible"><p id="cfg_dts">';
    	$out .= '<span class="dashicons dashicons-warning" style="color: #00a0d2"></span> ';
        $out .= __z('Zetaflix requires you to update the database'). ' <a href="'.admin_url('admin-ajax.php?action=zetaflixcleanerdatabase').'"><strong>'. __z('click here to update') .'</strong></a>';;
        $out .= '</p></div>';
        echo $out;
    }

    public function activate_omegadb(){
        $out = '<div class="notice notice-info is-dismissible activate_omegadb_true"><p id="ac_dbm_not">';
    	$out .= '<span class="dashicons dashicons-warning" style="color: #00a0d2"></span> ';
    	$out .= __z('Add API key for OmegaDB'). ' <a href="' .admin_url('admin.php?page=omgdb-settings').'"><strong>'.__z('Click here').'</strong></a>';
        $out .= '</p></div>';
        echo $out;
    }

    public function generate_pages(){
        $out = '<div class="notice notice-info is-dismissible activate_omegadb_true"><p id="ac_dbm_not">';
    	$out .= '<span class="dashicons dashicons-warning" style="color: #00a0d2"></span> ';
    	$out .= __z('Generate all the required pages'). ' <a href="'.admin_url('admin-ajax.php?action=zetaflixgeneratepage').'"><strong>'. __z('click here') .'</strong></a>';
    	$out .= '<button type="button" class="notice-dismiss zetadatabasetool" data-run="closenoti" data-noti="pages" data-num="'.wp_create_nonce('zetadatabasetoolnonce').'"><span class="screen-reader-text">Dismiss this notice.</span></button>';
        $out .= '</p></div>';
        echo $out;
    }

    public function update_linksmodule(){
        $out = '<div class="notice notice-info is-dismissible activate_omegadb_true"><p id="ac_dbm_not">';
    	$out .= '<span class="dashicons dashicons-warning" style="color: #00a0d2"></span> ';
    	$out .= __z('This version requires you to update the links module'). ' <a href="'.admin_url('tools.php?page=zetaflix-database').'"><strong>'. __z('click here') .'</strong></a>';
        $out .= '</p></div>';
        echo $out;
    }
	
    public function generate_widgets(){
        $out = '<div class="notice notice-info is-dismissible activate_omegadb_true"><p id="ac_dbm_not">';
    	$out .= '<span class="dashicons dashicons-warning" style="color: #00a0d2"></span> ';
    	$out .= __z('Set recommended widgets for sidebars'). ' <a href="'.admin_url('admin-ajax.php?action=zetaflixgeneratewidgets').'" onclick="return confirm(\''.__z('This will replace any existing widgets you may have, Are you sure you want to continue?').'\');"><strong>'. __z('click here') .'</strong></a>';
    	$out .= '<button type="button" class="notice-dismiss zetadatabasetool" data-run="closenoti" data-noti="widgets" data-num="'.wp_create_nonce('zetadatabasetoolnonce').'"><span class="screen-reader-text">Dismiss this notice.</span></button>';
        $out .= '</p></div>';
        echo $out;
    }

    public function __destruct(){
        return false;
    }

}

new ZetaNotices;


// End notificator..
