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

class ZetaAuth{

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        // Log Out
        add_action('wp_ajax_zetaflix_logout', array($this, 'Action_LogoutUser') );
        // Login / signup
		add_action('wp_ajax_nopriv_zetaflix_login', array($this, 'Action_LoginUser'));
		add_action('wp_ajax_nopriv_zetaflix_register', array($this, 'Action_RegisterUser'));
        // Action delete transient
        add_action('init', array($this, 'clean_SiteTransient'));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __destruct(){
		return false;
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function LoginForm(){
        $redirect = ( is_ssl() ? 'https://' : 'http://').$_SERVER['HTTP_HOST'].$_SERVER['REQUEST_URI'];
        $register = zeta_compose_pagelink('pageaccount'). '?action=signup';
        $lostpassword = esc_url(site_url('wp-login.php?action=lostpassword','login_post'));
        require_once(ZETA_DIR.'/inc/parts/login_form.php');
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Action_LogoutUser(){
        wp_destroy_current_session();
        wp_clear_auth_cookie();
        wp_send_json(array('response' => true));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Action_LoginUser(){
        $response = array();
        $username = zeta_isset($_POST,'log');
        $password = zeta_isset($_POST,'pwd');
        $redirect = zeta_isset($_POST,'red');
        $remember = zeta_isset($_POST,'rmb') ? true : false;
        $loginuser = $this->LoginUser($username, $password, $remember);
        if($loginuser){
            $response = array(
                'response' => true,
                'redirect' => esc_url($redirect),
                'message'  => __z('Welcome, reloading page')
            );
        }else{
            $response = array(
                'response' => false,
                'message'  => __z('Wrong username or password')
            );
        }
        // End Action
        wp_send_json($response);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Action_RegisterUser(){
        $response = array();
        if(zetaflix_google_recaptcha() === true){
            $data = array(
                'username'  => zeta_isset($_POST,'username'),
                'password'  => zeta_isset($_POST,'spassword'),
                'firstname' => zeta_isset($_POST,'firstname'),
                'lastname'  => zeta_isset($_POST,'lastname'),
                'email'     => zeta_isset($_POST,'email')
            );
            if(!zeta_isset($data,'username'))
                $response = array('response' => false,'message' => __z('A username is required for registration'));
            elseif(username_exists(zeta_isset($data,'username')))
                $response = array('response' => false,'message' => __z('Sorry, that username already exists'));
            elseif(!is_email(zeta_isset($data,'email')))
                $response = array('response' => false,'message' => __z('You must enter a valid email address'));
            elseif(email_exists(zeta_isset($data,'email')))
                $response = array('response' => false,'message' => __z('Sorry, that email address is already used'));
            elseif(!$this->RegisterUser($data))
                $response = array('response' => false,'message' => __z('Unknown error'));
            else{
                $this->LoginUser( zeta_isset($data,'username'), zeta_isset($data,'password'), true);
                $response = array('response' => true,'message' => __z('Registration completed successfully'), 'redirect' => zeta_compose_pagelink('pageaccount'));
            }
        } else {
            $response = array('response' => false,'message' => __z('Google reCAPTCHA Error'));
        }
        // End Action
        wp_send_json($response);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function LoginUser($username, $password, $remember = true){
        $auth = array();
        $auth['user_login']    = $username;
        $auth['user_password'] = $password;
        $auth['remember']      = $remember;
        $login = wp_signon($auth, false);
        if(!is_wp_error($login)){
            return true;
        }else{
            return false;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function RegisterUser($data){
        if(is_array($data)){
            $new_user = array(
                'user_pass'  => zeta_isset($data,'password'),
                'user_login' => esc_attr(zeta_isset($data,'username')) ,
                'user_email' => esc_attr(zeta_isset($data,'email')) ,
                'first_name' => zeta_isset($data,'firstname'),
                'last_name'	 => zeta_isset($data,'lastname'),
                'role'		 => 'subscriber',
            );
			$new_user = apply_filters('zetaflix_register', $new_user);
            return wp_insert_user($new_user);
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function ChangePasswordUser($user_id, $new_password){
        // soon..
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function ChangeEmailUser($user_id, $new_email){
        // soon..
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function NotifyLogin($user_id){
        // soon..
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function NotifyChanges($user_id, $notice_type){
        // soon..
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function JsonHeader(){
        header('Access-Control-Allow-Origin:*');
        header('Content-type: application/json');
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function clean_SiteTransient(){
        if(zeta_isset($_GET,'zeta_transient') == 'delete'){
            delete_transient('zetaflix_website');
        }
    }
}

new ZetaAuth;
