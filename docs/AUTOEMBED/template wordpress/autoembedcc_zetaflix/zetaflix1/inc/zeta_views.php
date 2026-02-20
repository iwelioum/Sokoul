<?php if(!defined('ABSPATH')) die;
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
*
* @since 1.0.0
*
*/

class ZetaFlixViews{

    /**
     * @since 3.4.0
     * @version 1.0
     */
    function __construct(){
        if(ZETA_THEME_VIEWS_COUNT == true){
            add_action('wp_ajax_zetaflix_viewcounter', array($this,'ajax'));
            add_action('wp_ajax_nopriv_zetaflix_viewcounter', array($this,'ajax'));
        }
    }

    /**
     * @since 3.4.0
     * @version 1.0
     */
    public static function ajax(){
        // Post data
        $post_id = zeta_isset($_POST,'post_id');
        // Set Response
        $response = array(
            'success' => false
        );
        // Verify post data
        if($post_id){
            $response = array(
                'success'  => true,
                'counting' => self::Counter($post_id)
            );
        }
        // Send json
        wp_send_json($response);
    }

    /**
     * @since 3.4.0
     * @version 1.0
     */
    public static function Counter($post_id = ''){
        if(!ZETA_THEME_VIEWS_COUNT) return '';
        $counting = get_post_meta($post_id,'zt_views_count',true);
        if(!$counting){
            $counting = 1;
        }else{
            $counting++;
        }
        update_post_meta($post_id,'zt_views_count',$counting);
        return $counting;
    }

    /**
     * @since 3.4.0
     * @version 1.0
     */
    public static function Meta($post_id = ''){
        // Verify module active
        if(!ZETA_THEME_VIEWS_COUNT) return '';
        // switching Options
        switch(zeta_get_option('view_count_mode','regular')){
            case 'regular':
                return self::Counter($post_id);
                break;
            case 'ajax':
                echo "<meta id='zetaflix-ajax-counter' data-postid='{$post_id}'/>";
                break;
            case 'none':
                return '';
            break;
        }
    }

}


new ZetaFlixViews;
