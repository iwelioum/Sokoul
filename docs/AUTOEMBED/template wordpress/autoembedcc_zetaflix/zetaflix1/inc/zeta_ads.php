<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
**************
* @since 1.0.0
*/

if(!class_exists('ZetaAds')){
    class ZetaAds{

        /**
         * @since 1.0.0
         * @version 1.0
         */
        public function __construct(){
            add_action('admin_menu', array($this,'admin_menu'));
            add_action('wp_ajax_zetaadmanage', array($this,'save_option'));
        }

        /**
         * @since 1.0.0
         * @version 1.0
         */
        public function admin_menu(){
            add_submenu_page(
                'themes.php',
                __z('ZetaFlix Ad banners'),
                __z('ZetaFlix Ad banners'),
                'manage_options',
                'zetaflix-ad',
                array(&$this,'admin_page')
            );
        }

        /**
         * @since 1.0.0
         * @version 1.0
         */
        public function admin_page(){
            // Security nonce
            $nonce = wp_create_nonce('zetaadsaveoptions');
            // Get Options
            $headcode = get_option('_zetaflix_header_code');
            $footcode = get_option('_zetaflix_footer_code');
            $adhomedk = get_option('_zetaflix_adhome');
            $adhomemb = get_option('_zetaflix_adhome_mobile');
            $adhomedk2 = get_option('_zetaflix_adhome2');
            $adhomemb2 = get_option('_zetaflix_adhome2_mobile');
            $adhomedk3 = get_option('_zetaflix_adhome3');
            $adhomemb3 = get_option('_zetaflix_adhome3_mobile');
            $adsingdk = get_option('_zetaflix_adsingle');
            $adsingmb = get_option('_zetaflix_adsingle_mobile');
            $adarchdk = get_option('_zetaflix_adarchive');
            $adarchmb = get_option('_zetaflix_adarchive_mobile');
            $adarchdk2 = get_option('_zetaflix_adarchive2');
            $adarchmb2 = get_option('_zetaflix_adarchive2_mobile');
            $adplaydk = get_option('_zetaflix_adplayer');
            $adplaymb = get_option('_zetaflix_adplayer_mobile');
            $adlinktd = get_option('_zetaflix_adlinktop');
            $adlinktm = get_option('_zetaflix_adlinktop_mobile');
            $adlinkbd = get_option('_zetaflix_adlinkbottom');
            $adlinkbm = get_option('_zetaflix_adlinkbottom_mobile');
            require_once(ZETA_DIR.'/inc/parts/admin/ads_tool.php');
        }

        /**
         * @since 1.0.0
         * @version 1.0
         */
        public function save_option(){
            $nonce = zeta_isset($_POST,'nonce');
            $response = false;
            if(wp_verify_nonce($nonce,'zetaadsaveoptions')){
                $options = array(
                    '_zetaflix_header_code',
                    '_zetaflix_footer_code',
                    '_zetaflix_adhome',
                    '_zetaflix_adhome_mobile',
                    '_zetaflix_adhome2',
                    '_zetaflix_adhome2_mobile',
                    '_zetaflix_adhome3',
                    '_zetaflix_adhome3_mobile',
                    '_zetaflix_adsingle',
                    '_zetaflix_adsingle_mobile',
                    '_zetaflix_adarchive',
                    '_zetaflix_adarchive_mobile',
                    '_zetaflix_adarchive2',
                    '_zetaflix_adarchive2_mobile',
                    '_zetaflix_adplayer',
                    '_zetaflix_adplayer_mobile',
                    '_zetaflix_adlinktop',
                    '_zetaflix_adlinktop_mobile',
                    '_zetaflix_adlinkbottom',
                    '_zetaflix_adlinkbottom_mobile'
                );
                foreach($options as $key) {
                    $value = zeta_isset($_POST,$key);
                    if($value){
                        update_option($key,$value);
                    } else {
                        update_option($key,false);
                    }
                }
                $response = true;
            } else {
                $response = false;
            }
            // The Response
            wp_send_json(array('success' => $response));
        }

        /**
         * @since 1.0.0
         * @version 1.0
         */
        private function textarea($id, $value, $placeholder = false){
            echo "<textarea id='unique{$id}' name='{$id}' rows='5' class='code' placeholder='{$placeholder}'>".esc_textarea(stripslashes($value))."</textarea>";
        }
    }

    new ZetaAds;
}
