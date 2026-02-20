<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

class OmegadbEPSEMboxes extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        if(is_user_logged_in() && !current_user_can('subscriber')){
            if($this->Disset($_GET,'action') == 'edit'){
                add_action('add_meta_boxes', array(&$this,'Register'));
            }
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Register(){
        //add_meta_box('omegadb_metabox_quick_publisher', __z('Quick publisher'), array(&$this,'Quick_publisher'), 'tvshows', 'normal', 'low');
        add_meta_box('omegadb_metabox_tvshows', __z('Seasons'), array(&$this,'TVShows'), 'tvshows', 'normal', 'low');
        add_meta_box('omegadb_metabox_seasons', __z('Episodes'), array(&$this,'Seasons'), 'seasons', 'normal', 'low');
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Quick_publisher(){
        require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/quick_publisher_form.php');
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function TVShows(){
        global $post;
        $tmdb = get_post_meta( $post->ID, 'ids', true);
        $gnrt = get_post_meta( $post->ID, 'clgnrt', true);
        $seas = $this->GetSeasons($tmdb);
        $sbtn = ($gnrt) ? __z('Generate new seasons') : __z('Generate Seasons');
        require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/seasons_generator.php');
        if($seas){
            $this->SeasonsView($seas, $tmdb);
        } else {
            echo '<div class="omegadb-no-content"><p>'.__z('There is not yet content to show').'</p></div>';
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    Public function Seasons(){
        global $post;
        $tmdb = get_post_meta( $post->ID, 'ids', true);
        $seas = get_post_meta( $post->ID, 'temporada', true);
        $gnrt = get_post_meta( $post->ID, 'clgnrt', true);
        $epsd = $this->GetEpisodes($tmdb,$seas);
        $sbtn = ($gnrt) ? __z('Generate new episodes') : __z('Generate Episodes');
        require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/episodes_generator.php');
        if($epsd){
            $this->EpisodesViews($epsd,$tmdb,$seas);
        } else {
            echo '<div class="omegadb-no-content"><p>'.__z('There is not yet content to show').'</p></div>';
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function GetSeasons($tmdb = ''){
        // Define Seasons
        $seasons = array();
        // Start Query
        $query = self::GetAllSeasons($tmdb);
        if($query){
            foreach($query as $postid){
                $seasons[] = array(
                    'id' => $postid,
                    'tv' => $tmdb,
                    'se' => get_post_meta($postid, 'temporada', true),
                    'im' => get_post_meta($postid, 'zt_poster', true),
                    'zt' => get_post_meta($postid, 'air_date', true),
                    'ti' => get_the_title($postid),
                    'pl' => get_permalink($postid),
                    'el' => admin_url('post.php?post='.$postid.'&action=edit'),
                );
            }
        }
        return apply_filters('omegadb_get_all_seasons', $seasons, $tmdb);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function GetEpisodes($tmdb = '', $season = ''){
        // Define Episodes
        $episodes = array();
        // Start Query
        $query = self::GetAllEpisodes($tmdb,$season);
        // Verify Query
        if($query){
            foreach($query as $postid){
                $episodes[] = array(
                    'id' => $postid,
                    'tv' => $tmdb,
                    'se' => $season,
                    'ep' => get_post_meta($postid, 'episodio', true),
                    'im' => get_post_meta($postid, 'zt_backdrop', true),
                    'zt' => get_post_meta($postid, 'air_date', true),
                    'nm' => get_post_meta($postid, 'episode_name', true),
                    'ti' => get_the_title($postid),
                    'pl' => get_permalink($postid),
                    'el' => admin_url('post.php?post='.$postid.'&action=edit')
                );
            }
        }
        return apply_filters('omegadb_get_all_episodes', $episodes, $tmdb.$season);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function SeasonsView($query = array(), $tmdb = ''){
        if(is_array($query)){
            $html_out = "<div class='omegadb_seasons_list' id='tmdb-$tmdb'>";
            foreach($query as $key => $val){
                // Compose Data
                $id = $this->Disset($val,'id');
                $ti = $this->Disset($val,'ti');
                $el = $this->Disset($val,'el');
                $pl = $this->Disset($val,'pl');
                $im = $this->Disset($val,'im');
                $dt = $this->Disset($val,'zt');
                $dt = isset($dt) ? zeta_date_compose($dt,false) : __z('date not defined');
                // View
                $html_out .= "<article class='item' id='post-season-$id'>";
                if(!get_post_meta($id,'clgnrt',true)){
                    $html_out .= "<div class='generator'><a href='$el&generate_episodes=all' target='_blank' class='button button-secundary omgdb_generate_episodes'>".__z('Get episodes')."</a></div>";
                }
                $html_out .= "<div class='image'><img src='".$this->ComposeTMDbImage($im,'seasons')."'></div>";
                $html_out .= "<div class='content'>";
                $html_out .= "<h3>$ti</h3>";
                $html_out .= "<p class='date'>$dt</p>";
                $html_out .= "<span><a href='$el'>".__z('Edit')."</a></span>";
                $html_out .= "<span><a href='$pl'>".__z('View')."</a></span>";
                $html_out .= "<span><a href='".get_delete_post_link($id)."' class='components-button is-link is-destructive'>".__z('Delete')."</a></span>";
                $html_out .= "</div></article>";
            }
            $html_out .= "</div>";
            // Filter views
            echo apply_filters('omegadb_get_html_seasons', $html_out, $tmdb);
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function EpisodesViews($query = array(), $tmdb = '', $season = ''){
        if(is_array($query)){
            $html_out = "<div class='omegadb_seasons_list episodes' id='tmdb-$tmdb-$season'>";
            foreach($query as $key => $val){
                // Compose data
                $id = $this->Disset($val,'id');
                $im = $this->Disset($val,'im');
                $ti = $this->Disset($val,'ti');
                $el = $this->Disset($val,'el');
                $pl = $this->Disset($val,'pl');
                $nm = $this->Disset($val,'nm');
                $dt = $this->Disset($val,'zt');
                $dt = isset($dt) ? zeta_date_compose($dt,false) : __z('date not defined');
                // View
                $html_out .= "<article class='item' id='post-episode-$id'>";
                $html_out .= "<div class='image'><img src='".$this->ComposeTMDbImage($im,'episodes')."'></div>";
                $html_out .= "<div class='content'>";
                $html_out .= "<h3>$ti</h3>";
                $html_out .= "<p class='date'><strong>$nm</strong> <small>$dt</small></p>";
                $html_out .= "<span><a href='$el'>".__z('Edit')."</a></span>";
                $html_out .= "<span><a href='$pl'>".__z('View')."</a></span>";
                $html_out .= "<span><a href='".get_delete_post_link($id)."' class='components-button is-link is-destructive'>".__z('Delete')."</a></span>";
                $html_out .= "</div></article>";
            }
            $html_out .= "</div>";
            // Filter views
            echo apply_filters('omegadb_get_html_episodes', $html_out, $tmdb.$season);
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function ComposeTMDbImage($path = '', $type = ''){
        $path_assts = OMEGADB_URI.'/assets/';
        $path_local = ($type == 'seasons') ? 'no_img_sea.png' : 'no_img_epi.png';
        if(!empty($path)){
            if(!filter_var($path, FILTER_VALIDATE_URL)){
                return 'https://image.tmdb.org/t/p/w92'.$path;
            } else {
                return $path;
            }
        } else {
            return $path_assts.$path_local;
        }
    }
}

new OmegadbEPSEMboxes;
