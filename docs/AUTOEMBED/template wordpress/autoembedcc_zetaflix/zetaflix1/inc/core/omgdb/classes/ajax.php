<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

class OmegadbAjax extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function __construct(){
        add_action('wp_ajax_omegadb_genereditor', array(&$this,'genereditor'));
        add_action('wp_ajax_omegadb_updatedimdb', array(&$this,'updatedimdb'));
        add_action('wp_ajax_omegadb_savesetting', array(&$this,'savesettings'));
        add_action('wp_ajax_omegadb_insert_tmdb', array(&$this,'tmdbinsert'));
		add_action('wp_ajax_omegadb_update_tvss', array(&$this,'tvssupdate'));
        add_action('wp_ajax_omegadb_generate_se', array(&$this,'tmdbseasons'));
        add_action('wp_ajax_omegadb_generate_ep', array(&$this,'tmdbepisodes'));
        add_action('wp_ajax_omegadb_generate_te', array(&$this,'tmdbseasepis'));
        add_action('wp_ajax_omegadb_clean_cache', array(&$this,'cleancaching'));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function cleancaching(){
        if(is_user_logged_in() && current_user_can('administrator')){
            foreach(glob(OMEGADB_CACHE_DIR."*") as $file){
                if(is_file($file)) unlink($file);
            }
            wp_redirect(esc_url($this->Disset($_SERVER,'HTTP_REFERER')),302);
            exit;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function tmdbseasepis(){
        if(!empty($_POST)){
            $type = $this->Disset($_POST,'type');
            $tmdb = $this->Disset($_POST,'tmdb');
            $name = $this->Disset($_POST,'name');
            $seas = $this->Disset($_POST,'seas');
            $item = $this->Disset($_POST,'item');
            $totl = $this->Disset($_POST,'totl');
            $post = $this->Disset($_POST,'pare');
            $meta = get_post_meta( $post,'clgnrt',true);
            if(!$meta){
                update_post_meta($post, 'clgnrt', '1');
            }
            switch($type){
                case 'seasons':
                    new OmegadbImporters('seasons', array('id' => $tmdb, 'se' => $item, 'nm' => $name, 'ed' => false));
                    break;

                case 'episodes':
                    new OmegadbImporters('episodes', array('id' => $tmdb, 'se' => $seas, 'ep' => $item, 'nm' => $name, 'ed' => false));
                    break;
            }
        }
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function genereditor(){
        $type = $this->Disset($_POST,'typept');
        $post = $this->Disset($_POST,'idpost');
        $tmdb = $this->Disset($_POST,'tmdbid');
        $seas = $this->Disset($_POST,'season');
        $epis = $this->Disset($_POST,'episde');
        $name = $this->Disset($_POST,'tvname');
        switch($type){
            case 'movies':
            case 'tvshows':
                new OmegadbImporters($type, array('id' => $tmdb, 'ed' => $post));
                break;
            case 'seasons':
                new OmegadbImporters('seasons', array('id' => $tmdb, 'se' => $seas, 'nm' => $name, 'ed' => $post));
                break;
            case 'episodes':
                new OmegadbImporters('episodes', array('id' => $tmdb, 'se' => $seas, 'ep' => $epis, 'nm' => $name, 'ed' => $post));
                break;
            default:
                wp_send_json(array('response' => false,'message' => __z('Complete required data')));
                break;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function updatedimdb(){
        // POST Data
        $post = $this->Disset($_POST,'id');
        $imdb = $this->Disset($_POST,'imdb');
        if($post && $imdb){
            // Api Rest
            $rest = self::UpdateIMDb($imdb,$post);
            // HTML
            if($this->Disset($rest,'imdb')){
                echo $this->Disset($rest,'rating');
            } else {
                echo $this->Disset($rest,'message');
            }
        }
        wp_die();
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function savesettings(){
        // POST Data
        $nonce = $this->Disset($_POST,'cnonce');
        $stngs = $this->Disset($_POST,'omgdbettings');
        $relod = false;
        // Verifications
        if(is_array($stngs) && wp_verify_nonce($nonce,'omegadb-save-settings')){
            if($this->Disset($stngs,'omegadb') !== $this->get_option('omegadb')){
                delete_transient('omegadb_activator');
                $relod = true;
            }
            if($this->Disset($stngs,'themoviedb') !== $this->get_option('themoviedb')){
                delete_transient('themoviedb_activator');
                $relod = true;
            }
            if($this->Disset($stngs,'updatermethod') !== $this->get_option('updatermethod')){
                wp_clear_scheduled_hook('omegadb_cron_metaupdater');
                $relod = true;
            }
            $update = update_option(OMEGADB_OPTIONS,$stngs);
            if($update){
                $response = array(
                    'response' => true,
                    'message' => __z('Settings saved'),
                    'reload' => $relod
                );
            }else{
                $response = array(
                    'response' => true,
                    'message' => __z('No changes to save'),
                    'reload' => $relod
                );
            }
        } else {
            $response = array(
                'response' => false,
                'message' => __z('Validation is not completed'),
                'reload' => $relod
            );
        }
        // The Json Response
        wp_send_json($response);
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function tmdbinsert(){
        // Post Data
        $type = $this->Disset($_REQUEST,'ptype');
        $tmdb = $this->Disset($_REQUEST,'ptmdb');
        // Nonce condiction
        if($type && $tmdb){
            new OmegadbImporters($type, array('id' => $tmdb, 'ed' => false));
        }else{
            wp_send_json(array('response' => false,'message' => __z('Complete required data')));
        }
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function tmdbseasons(){
        // Post Data
        $tmdb = $this->Disset($_REQUEST,'tmdb');
        $tmse = $this->Disset($_REQUEST,'tmse');
        $tnam = $this->Disset($_REQUEST,'name');
        $post = $this->Disset($_REQUEST,'post');
        $meta = get_post_meta( $post,'clgnrt',true);
        if(!$meta){
            update_post_meta( $post, 'clgnrt', '1');
        }
        // Verify
        if($tmdb && $tmse && $tnam){
            $season_data = array(
                'id' => $tmdb,
                'se' => $tmse,
                'nm' => $tnam,
                'ed' => false,
            );
            new OmegadbImporters('seasons',$season_data);
        } else {
            wp_send_json(array('response' => false,'message' => __z('Complete required data')));
        }
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function tmdbepisodes(){
        // Post Data
        $tmdb = $this->Disset($_REQUEST,'tmdb');
        $tmse = $this->Disset($_REQUEST,'tmse');
        $tmep = $this->Disset($_REQUEST,'tmep');
        $tnam = $this->Disset($_REQUEST,'name');
        $post = $this->Disset($_REQUEST,'post');
        $meta = get_post_meta( $post,'clgnrt',true);
        if(!$meta){
            update_post_meta( $post, 'clgnrt', '1');
        }
        // Verify
        if($tmdb && $tmse && $tmep && $tnam){
            $episode_data = array(
                'id' => $tmdb,
                'se' => $tmse,
                'ep' => $tmep,
                'nm' => $tnam,
                'ed' => false,
            );
            new OmegadbImporters('episodes',$episode_data);
        }else{
            wp_send_json(array('response' => false,'message' => __z('Complete required data')));
        }
    }
	
    public function tvssupdate(){
        // Post Data
		
        $type = $this->Disset($_REQUEST,'type');
        $pid = $this->Disset($_REQUEST,'id');
		
		$status = 0;
		$update_tvid = false;
		$update_ssid = false;
        // Nonce condiction
        if($type && $pid){
			// Transient data

			$ids = get_post_meta($pid, 'ids', true);
			if($ids){
				
				if($type == 'tvss' OR $type == 'tvid'){

					$tv_args = array('numberposts' => 1, 'fields' => 'ids', 'post_type' => 'tvshows', 'post_status' => 'publish', 'meta_query' => array('relation' => 'AND', array('key' => 'ids','value' => $ids, 'compare' => '=')));								
					
						$tvfile = glob(OMEGADB_CACHE_UPD_DIR.'*_tvshow.'.$ids);
						$ssfile = $tvfile[0];
						if(file_exists($tvfile) && filemtime($tvfile) + OMEGADB_CACHE_TIM >= time()){
							$tvid = file_get_contents($tvfile);
							$tvid = maybe_unserialize($tvid);
						}else{
							$tvid = get_posts($tv_args);
							$tvid = $tvid[0];	

							if($tvid){
								file_put_contents(OMEGADB_CACHE_UPD_DIR.$tvid.'_tvshow.'.$ids,serialize($ssid));
							}

						}
					
					if($tvid){
						$update_tvid = update_post_meta($pid, 'tvshowid', $tvid);
					}				
				
				}			
				
				if($type == 'tvss' OR $type == 'ssid'){
					$ss = get_post_meta($pid, 'temporada', true);	
					if($ss){

						$ss_args = array('numberposts' => 1, 'fields' => 'ids', 'post_type' => 'seasons', 'post_status' => 'publish', 'meta_query' => array('relation' => 'AND', array('key' => 'ids','value' => $ids, 'compare' => '='), array('key' => 'temporada','value' => $ss, 'compare' => '=')) );
						
						$ssfile = glob(OMEGADB_CACHE_UPD_DIR.'*_season_'.$ss.'.'.$ids);
						$ssfile = $ssfile[0];
						if(file_exists($ssfile) && filemtime($ssfile) + OMEGADB_CACHE_TIM >= time()){
							$ssid = file_get_contents($ssfile);
							$ssid = maybe_unserialize($ssid);
						}else{
							$ssid = get_posts($ss_args);
							$ssid = $ssid[0];					

							if($ssid){
								file_put_contents(OMEGADB_CACHE_UPD_DIR.$ssid.'_season_'.$ss.'.'.$ids,serialize($ssid));
							}
						}
											
						if($ssid){
							$update_ssid = update_post_meta($pid, 'seasonid', $ssid);
						}
					}
				
				}			
				
			switch($type){
				case 'tvss':
				$status = ($tvid && $update_tvid == true && $ssid && $update_ssid == true) ? 1 : 0;
				break;
				case 'tvid':
				$status = ($tvid && $update_tvid == true) ? 1 : 0;
				break;
				case 'ssid':
				$status = ($ssid && $update_ssid == true) ? 1 : 0;
				break;
				default:
				$status = 0;
			}

            wp_send_json(array('status' => $status, 'id' => $pid, 'ssid' => $ssid, 'tvid' => $tvid));

        }else{
            wp_send_json(array('status' => 0,'message' => __z('Complete required data')));
        }
		
		}
    }
}

new OmegadbAjax;
