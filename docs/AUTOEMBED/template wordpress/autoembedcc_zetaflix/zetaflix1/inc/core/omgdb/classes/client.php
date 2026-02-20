<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/


class OmegadbClient extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        add_action('init', array($this,'InsertData'));
        add_action('init', array($this,'Deactivation'));
        if(is_user_logged_in() && current_user_can('administrator')){
            add_action('admin_init', array($this,'OMGDBActivation'));
            add_action('admin_init', array($this,'TMDbActivation'));
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function InsertData(){
        $action = $this->Disset($_REQUEST,'omgdb-action');
        if($action == 'import'){
            $type = $this->Disset($_REQUEST,'type');
            $data = array('id' => $this->Disset($_REQUEST,'id'));
            header('Access-Control-Allow-Origin: *');
            header('Content-type: application/json');
            new OmegadbImporters($type, $data);
            exit;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function ApiRest(){
        // soon..
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function TMDbActivation(){
        $apikey = $this->get_option('themoviedb');
        $trasient = get_transient('themoviedb_activator');
        if($apikey){
            if($trasient === false){
                $args = array(
                    'api_key' => $apikey,
                );
                $rest = $this->RemoteJson($args, OMEGADB_TMDBAPI.'/authentication/guest_session/new');
                if(!is_wp_error($rest) && $this->Disset($rest,'success') == true){
                    $data = array(
                        'response' => true,
                        'session' => $this->Disset($rest,'guest_session_id'),
                        'time' => time()
                    );
                    set_transient('themoviedb_activator', $data, 1 * HOUR_IN_SECONDS);
                }
            }
        }else{
            if($trasient){
                delete_transient('themoviedb_activator');
            }
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function OMGDBActivation(){
        $apikey = $this->get_option('omegadb');
        $trasient = get_transient('omegadb_activator');
        if($apikey){
            if($trasient === false){
                $post = array(
                    'timeout'   => 15,
                    'sslverify' => true,
                    'body' => array(
                        'process' => 'activate',
                        'domain'   => get_option('siteurl'),
                        'license'  => $apikey,
                        'ip' => $this->Disset($_SERVER,'SERVER_ADDR')
                    )
                );
                $rest = wp_remote_get(OMEGADB_DBMVAPI,$post);
                $data = array('status' => 'verifying', 'apikey' => $apikey);
				
                if(!is_wp_error($rest)){
                    $data = wp_remote_retrieve_body($rest);
                    $data = maybe_unserialize($data);

                }
				
                //Set Action
                set_transient('omegadb_activator', $data, 1 * HOUR_IN_SECONDS);
            }
        }else{
            if($trasient){
                delete_transient('omegadb_activator');
            }
        }
		
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function Deactivation(){
        // Post Data
		
        $action = $this->Disset($_POST,'omgdb-action');
        $apikey = $this->Disset($_POST,'omgdb-apikey');
        // Verify Method and Action
        if($this->Disset($_SERVER,'REQUEST_METHOD') === 'POST' && $action == 'deactivate'){
            // Compose pre-response
            $response = array(
                'response' => false,
                'message' => 'no_access'
            );
            // Verify Api key
            if($apikey === $this->get_option('omegadb')){
                // Delete Server Information
                $this->set_option('omegadb','');
               // delete_transient('omegadb_activator');
                // The Response
                $response = array(
                    'response' => true,
                    'message' => 'completed'
                );
            }
            // Echo Json Response
            wp_send_json($response);
        }
    }
}

new OmegadbClient;
