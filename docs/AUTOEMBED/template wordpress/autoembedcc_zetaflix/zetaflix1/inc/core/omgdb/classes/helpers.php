<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/


class OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public static function ReportsIssues($issue){
		switch($issue) {
            case 'labeling':
				$response = [
					'title'    => __z('Labeling problem'),
					'subtitle' =>  __z('Wrong title or summary, or episode out of order')
				];
			break;

            case 'video':
				$response = [
					'title'    => __z('Video Problem'),
					'subtitle' => __z('Blurry, cuts out, or looks strange in some way')
				];
			break;

            case 'audio':
				$response = [
					'title'    => __z('Sound Problem'),
					'subtitle' => __z('Hard to hear, not matched with video, or missing in some parts')
				];
			break;

            case 'caption':
				$response = [
					'title'    => __z('Subtitles or captions problem'),
					'subtitle' => __z('Missing, hard to read, not matched with sound, misspellings, or poor translations')
				];
			break;

            case 'buffering':
				$response = [
					'title'    => __z('Buffering or connection problem'),
					'subtitle' => __z('Frequent rebuffering, playback won\'t start, or other problem')
				];
			break;

            default:
				$response = [
					'title'    => __z('Unknown problem'),
					'subtitle' => __z('Problem not specified')
				];
			break;
		}
		return $response;
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function UpdateTypes(){
        // Types Verification
        if($this->get_option('updatermovies'))
            $types[] = 'movies';
        if($this->get_option('updatershows'))
            $types[] = 'tvshows';
        if($this->get_option('updaterseasons'))
            $types[] = 'seasons';
        if($this->get_option('updaterepisodes'))
            $types[] = 'episodes';
        // Compose response
        return $types;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function TimeExe($time = ''){
        $micro	= microtime(TRUE);
		return number_format($micro - $time, 2);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function OMGDBtatus(){
        $key = $this->get_option('omegadb');
        $sta = get_transient('omegadb_activator');
		$sta = maybe_unserialize($sta);
        $eco = 'jump status empty';
        if($key){
            if($sta){
                if(isset($sta['status']) && $sta['status'] == 'active'){
                    $eco = 'status valid';
                } else {
                    $eco = 'jump status invalid';
                }
            } else {
                $eco = 'verifying';
            }
        }
        return $eco;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function TMDbStatus(){
        $key = $this->get_option('themoviedb');
        $sta = get_transient('themoviedb_activator');
        $eco = 'jump status empty';
        if($key){
            if($sta){
                if(isset($sta['response']) && $sta['response'] == true){
                    $eco = 'status valid';
                } else {
                    $eco = 'jump status invalid';
                }
            } else {
                $eco = 'verifying';
            }
        }
        return $eco;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function InsertGenres($post_id = '', $type = ''){
        $tmdb = $this->Disset($_POST,'ids');
        $term = get_the_term_list($post_id,'genres');
        if($this->get_option('genres') == true && !empty($tmdb) && $term == false){
            $args = array(
                'language' => $this->get_option('language','en-US'),
                'api_key'  => $this->get_option('themoviedb',OMEGADB_TMDBKEY),
            );
            $rmtapi = $this->RemoteJson($args, OMEGADB_TMDBAPI.'/'.$type.'/'.$tmdb);
            $genres = $this->Disset($rmtapi,'genres');
            $insert = array();
            foreach($genres as $genre){
                $insert[] = $this->Disset($genre,'name');
            }
            wp_set_object_terms($post_id,$insert,'genres',false);
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Compose_Title_ID($string = ''){
        // It is a valid link
        if(filter_var($string, FILTER_VALIDATE_URL)){
            // Set counters at zero
            $icount = 0;
            $tcount = 0;
            // Verify link
            str_replace('imdb.com', null, $string, $icount);
            str_replace('themoviedb.org', null, $string, $tcount);
            if(isset($tcount) OR isset($icount)){
                // Is Themoviedb.org
                if($tcount){
                    $formt1 = explode('/tv/',$string);
                    $formt2 = explode('/movie/',$string);
                    if($this->Disset($formt1,1)){
                        $theid = explode('-',$this->Disset($formt1,1));
                        $theid = $this->Disset($theid,0);
                    }
                    if($this->Disset($formt2,1)){
                        $theid = explode('-',$this->Disset($formt2,1));
                        $theid = $this->Disset($theid,0);
                    }
                    return $theid;
                }
                // Is IMDb.com
                if($icount){
                    $theid = explode('/title/',$string);
                    $theid = explode('/',$this->Disset($theid,1));
                    return $this->Disset($theid,0);
                }
            } else {
                return false;
            }
        } else {
            // Is a simple ID
            return $string;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function get_option($option_name = '', $default = ''){
        $options = apply_filters('dmovies_get_option', get_option(OMEGADB_OPTIONS), $option_name, $default);
        if(!empty($option_name) && !empty($options[$option_name])){
            return $options[$option_name];
        } else {
            return (!empty($default)) ? $default : null;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function st_get_option($option_name = '', $default = ''){
        $options = apply_filters('dmovies_get_option', get_option(OMEGADB_OPTIONS), $option_name, $default);
        if(!empty($option_name) && !empty($options[$option_name])){
            return $options[$option_name];
        } else {
            return (!empty($default)) ? $default : null;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function set_option($option_name = '', $new_value = ''){
        $options = apply_filters('omegadb_set_option', get_option(OMEGADB_OPTIONS), $option_name, $new_value );
        if(!empty($option_name)){
            $options[$option_name] = $new_value;
            update_option(OMEGADB_OPTIONS, $options);
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function st_set_option($option_name = '', $new_value = ''){
        $options = apply_filters('omegadb_set_option', get_option(OMEGADB_OPTIONS), $option_name, $new_value );
        if(!empty($option_name)){
            $options[$option_name] = $new_value;
            update_option(OMEGADB_OPTIONS, $options);
        }
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function field_text($id = '', $default = '', $desc = '', $placeholder = ''){
        $option = $this->get_option($id, $default);
        $sedesc = !empty($desc) ? '<p>'.$desc.'</p>' : false;
        echo "<fieldset><input id='dbmv-input-{$id}' type='text' name='omgdbettings[{$id}]' value='{$option}' placeholder='{$placeholder}'>{$sedesc}</fieldset>";
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function field_textarea($id = '', $default = '', $desc = '', $placeholder = ''){
        $option = $this->get_option($id, $default);
        $sedesc = !empty($desc) ? '<p>'.$desc.'</p>' : false;
        echo "<fieldset><textarea id='dbmv-textarea-{$id}' name='omgdbettings[{$id}]' rows='5' placeholder='{$placeholder}'>{$option}</textarea>{$sedesc}<fieldset>";
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function field_number($id = '', $default = '', $desc = '', $placeholder = ''){
        $option = $this->get_option($id, $default);
        $sedesc = !empty($desc) ? '<p>'.$desc.'</p>' : false;
        echo "<fieldset><input id='dbmv-input-{$id}' type='number' name='omgdbettings[{$id}]' value='{$option}' placeholder='{$placeholder}'>{$sedesc}</fieldset>";
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function field_checkbox($id = '', $text = ''){
        $checked = checked($this->get_option($id), true, false);
        $out_html  = "<fieldset><label for=checkbox-$id>";
        $out_html .= "<input id='checkbox-{$id}' name='omgdbettings[{$id}]' type='checkbox' value='1' {$checked}> <span>{$text}</span>";
        $out_html .= "</label></fieldset>";
        echo $out_html;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function field_radio($id = '', $options = '', $default = ''){
        $option = $this->get_option($id,$default);
        $out_html = "";
        foreach($options as $key => $val){
            $checked = checked($option, $key, false);
            $out_html .= "<fieldset class='radio'><label for=checkbox-$id-$key>";
            $out_html .= "<input id='checkbox-$id-$key' name='omgdbettings[$id]' type='radio' value='$key' $checked> <span>$val</span>";
            $out_html .= "</label></fieldset>";
        }
        echo $out_html;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function field_select($id = '', $options ='', $default = '', $desc = ''){
        $option = $this->get_option($id, $default);
        $sedesc = !empty($desc) ? '<p>'.$desc.'</p>' : false;
        $out_html = "<fieldset><select id='select-$id' name='omgdbettings[$id]'>";
        foreach($options as $key => $val) {
            $out_html .= "<option value='$key' ".selected($option, $key, false).">$val</option>";
        }
        $out_html .= "</select>$sedesc</fieldset>";
        echo $out_html;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function ResponseJson($data = array()){
        if(is_array($data)){
            return json_encode($data);
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function SetUserPost($default = '1'){
        return is_user_logged_in() ? get_current_user_id() : $default;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function RemoteJson($args = array(), $api = ''){
        $sapi = esc_url_raw(add_query_arg($args,$api));
        $json = wp_remote_retrieve_body(wp_remote_get($sapi));
        return json_decode($json,true);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function SRemoteJson($args = array(), $api = ''){
        $sapi = esc_url_raw(add_query_arg($args,$api));
        $json = wp_remote_retrieve_body(wp_remote_get($sapi));
        return json_decode($json,true);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Disset($data ='', $key = ''){
        return isset($data[$key]) ? $data[$key] : null;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Tags($option ='', $data =''){
		// Taggable data
		if(is_array($data) && !empty($data)){
	        foreach($data as $key => $value) {
	            $option = str_replace('{'.$key.'}', $value, $option);
	        }
	    }
		// Filter and return
        return apply_filters('omegadb_tags_composer',$option, $data);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function TextCleaner($text = ''){
        return wp_strip_all_tags(html_entity_decode($text));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function VeryTMDb($meta_key = '', $tmdb_id = '', $post_type = ''){
        $query = array(
            'post_type' => $post_type,
            'meta_query' => array(
                array(
                    'key'   => $meta_key,
                    'value' => $tmdb_id
                )
            ),
            'posts_per_page' => 1
        );
        $query = new WP_Query($query);
        $query = wp_list_pluck($query->posts,'ID');
        return $this->Disset($query,0);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function VeryTMDbSE($tmdb = '', $season =''){
        $query = array(
            'post_type'  => 'seasons',
            'meta_query' => array(
                array('key' => 'ids', 'value' => $tmdb),
                array('key' => 'temporada', 'value' => $season)
            ),
            'posts_per_page' => 1
        );
        $query = new WP_Query($query);
        $query = wp_list_pluck($query->posts,'ID');
        return $this->Disset($query,0);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function VeryTMDbEP($tmdb = '', $season = '', $episode = ''){
        $query = array(
            'post_type'  => 'episodes',
            'meta_query' => array(
                array('key' => 'ids','value' => $tmdb),
                array('key' => 'temporada','value' => $season),
                array('key' => 'episodio','value' => $episode)
            ),
            'posts_per_page' => 1
        );
        $query = new WP_Query($query);
        $query = wp_list_pluck($query->posts,'ID');
        return $this->Disset($query,0);
    }


    /**
     * @since 1.0.1
     * @version 1.0
     */
    public function getImportImageURL($posttype = '', $poster = '', $backdrop ='') {

        $tmdb = 'https://image.tmdb.org/t/p/';
        $url = '';     

        switch ($posttype) {
            case 'ss':
                $size = $this->get_option('poster-size', 'w780');
                $file = $poster;
                break;
            case 'ep':
                $size = $this->get_option('backdrop-size', 'w1280');
                $file = $backdrop;
                break;
            case 'tv':
                $posterSource = $this->get_option('poster-source-tv', 'pstr');
                $size = ($posterSource == 'bckdrp') ? $this->get_option('backdrop-size', '1280') : $this->get_option('poster-size', 'w780');
                $file = ($posterSource == 'bckdrp') ? $backdrop : $poster;
                break;
            default:
                $posterSource = $this->get_option('poster-source', 'pstr');
                $size = ($posterSource == 'bckdrp') ? $this->get_option('backdrop-size', '1280') : $this->get_option('poster-size', 'w780');
                $file = ($posterSource == 'bckdrp') ? $backdrop : $poster;
                break;
        }
    
        return $tmdb . $size . $file;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    
    public function UploadImage($url = '', $post = '', $thumbnail = false, $showurl = false){
        
        if($this->get_option('upload') == true && !empty($url)){            
            // WordPress Lib
            require_once(ABSPATH.'wp-admin/includes/file.php');
			require_once(ABSPATH.'wp-admin/includes/image.php');
            // File System
            global $wp_filesystem;
            WP_Filesystem();
			// Get Image
			$upload_dir	  = wp_upload_dir();
			$image_remote = wp_remote_get($url);
			$image_data	  = wp_remote_retrieve_body($image_remote);
			$filename	  = wp_basename($url);
            if(!is_wp_error($image_data)){
                // Path folder
    			if(wp_mkdir_p($upload_dir['path'])) {
    				$file = $upload_dir['path'] . '/' . $filename;
    			} else {
    				$file = $upload_dir['basedir'] . '/' . $filename;
    			}
    			$wp_filesystem->put_contents($file, $image_data);
    			$wp_filetype = wp_check_filetype($filename, null);
    			// Compose attachment Post
    			$attachment = array(
    				'post_mime_type' => $wp_filetype['type'],
    				'post_title' => sanitize_file_name($filename),
    				'post_content' => false,
    				'post_status' => 'inherit'
    			);
    			// Insert Attachment
    			$attach_id	 = wp_insert_attachment($attachment, $file, $post);
    			$attach_data = wp_generate_attachment_metadata($attach_id, $file);
    			wp_update_attachment_metadata($attach_id, $attach_data );
    			// Featured Image
    			if($thumbnail == true) set_post_thumbnail($post, $attach_id);
    			if($showurl == true) return wp_get_attachment_url($attach_id);
            }

        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function UpdaterMethod(){
        return array(
            'wp-ajax' => __('Admin-Ajax'),

        );
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function UpdaterAuto(){
        return array(
            'weekly'    => __z('Weekly'),
            'monthly'   => __z('Monthly'),
            'quarterly' => __z('Quarterly'),
            'never'     => __z('Never')
        );
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function PostOrder(){
        return array(
            'ASC'  => __('Ascending'),
            'DESC' => __('Descending')
        );
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function PostStatus(){
        return array(
            'publish' => __('Publish'),
            'pending' => __('Pending'),
            'draft'   => __('Draft')
        );
    }    

    
    /**
     * @since 1.0.1
     * @version 1.0
     */
    public static function PosterSource(){
        $sources = array(
            'pstr' => __z('Poster Image'),
            'bckdrp' => __z('Backdrop Image')
        );

        return apply_filters('omegadb_tmdb_postersources',$sources);
    }

    /**
     * @since 1.0.1
     * @version 1.0
     */
    public static function PosterSize(){
        $postersizes = array(
            'w92' => __z('92x138'),
            'w154' => __z('154x231'),
            'w185'=> __z('185x278'),
            'w300'=> __z('300x450'),
            'w500'=> __z('500x750'),
            'w780'=> __z('780x1170'),
            'w1280'=> __z('1280x1920'),

        );
        return apply_filters('omegadb_tmdb_postersizes',$postersizes);
    }

    /**
     * @since 1.0.1
     * @version 1.0
     */
    public static function BackdropSize(){
        $postersizes = array(
            'w92' => __z('92x52'),
            'w154' => __z('154x87'),
            'w185'=> __z('185x104'),
            'w300'=> __z('300x169'),
            'w500'=> __z('500x281'),
            'w780'=> __z('780x439'),
            'w1280'=> __z('1280x720'),

        );
        return apply_filters('omegadb_tmdb_postersizes',$postersizes);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function Languages(){
        $languages = array(
			"ar-AR" => __z('Arabic'),
			"bs-BS" => __z('Bosnian'),
			"bg-BG" => __z('Bulgarian'),
			"hr-HR" => __z('Croatian'),
			"cs-CZ" => __z('Czech'),
			"da-DK" => __z('Danish'),
			"nl-NL" => __z('Dutch'),
			"en-US" => __z('English'),
			"fi-FI" => __z('Finnish'),
			"fr-FR" => __z('French'),
			"de-DE" => __z('German'),
			"el-GR" => __z('Greek'),
			"he-IL" => __z('Hebrew'),
			"hu-HU" => __z('Hungarian'),
			"is-IS" => __z('Icelandic'),
			"id-ID" => __z('Indonesian'),
			"it-IT" => __z('Italian'),
			"ko-KR" => __z('Korean'),
			"lb-LB" => __z('Letzeburgesch'),
			"lt-LT" => __z('Lithuanian'),
			"zh-CN" => __z('Mandarin'),
			"fa-IR" => __z('Persian'),
			"pl-PL" => __z('Polish'),
			"pt-PT" => __z('Portuguese'),
			"pt-BR" => __z('Brazilian Portuguese'),
			"ro-RO" => __z('Romanian'),
			"ru-RU" => __z('Russian'),
			"sk-SK" => __z('Slovak'),
			"es-ES" => __z('Spanish'),
			"es-MX" => __z('Spanish LA'),
			"sv-SE" => __z('Swedish'),
			"th-TH" => __z('Thai'),
			"tr-TR" => __z('Turkish'),
			"tw-TW" => __z('Twi'),
			"uk-UA" => __z('Ukrainian'),
			"vi-VN" => __z('Vietnamese')
		);
        return apply_filters('omegadb_tmdb_languages',$languages);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function GenresMovies(){
        $genres = array(
			''		=> __z('All genres'),
			'28'	=> __z('Action'),
			'12'	=> __z('Adventure'),
			'16'	=> __z('Animation'),
			'35'	=> __z('Comedy'),
			'80'	=> __z('Crime'),
			'99'	=> __z('Documentary'),
			'18'	=> __z('Drama'),
			'10751' => __z('Family'),
			'14'	=> __z('Fantasy'),
			'36'	=> __z('History'),
			'27'	=> __z('Horror'),
			'10402' => __z('Music'),
			'9648'	=> __z('Mystery'),
			'10749' => __z('Romance'),
			'878'	=> __z('Science Fiction'),
			'10770' => __z('TV Movie'),
			'53'	=> __z('Thriller'),
			'10752' => __z('War'),
			'37'	=> __z('Western')
		);
        $html_out ='';
        foreach($genres as $key => $name){
            $html_out .="<option value='$key'>$name</option>\n";
        }
        return apply_filters('omegadb_tmdb_genres_movies',$html_out);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function GenresTVShows(){
        $genres = array(
			null	=> __z('All genres'),
			'10759'	=> __z('Action & Adventure'),
			'16'	=> __z('Animation'),
			'35'	=> __z('Comedy'),
			'80'	=> __z('Crime'),
			'99'	=> __z('Documentary'),
			'18'	=> __z('Drama'),
			'10751'	=> __z('Family'),
			'10762'	=> __z('Kids'),
			'9648'	=> __z('Mystery'),
			'10763'	=> __z('News'),
			'10764'	=> __z('Reality'),
			'10765'	=> __z('Sci-Fi & Fantasy'),
			'10766'	=> __z('Soap'),
			'10767'	=> __z('Talk'),
			'10768'	=> __z('War & Politics'),
			'37'	=> __z('Western'),
		);
        $html_out ='';
        foreach($genres as $key => $name){
            $html_out .="<option value='$key'>$name</option>\n";
        }
        return apply_filters('omegadb_tmdb_genres_tvshows',$html_out);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function GetAllSeasons($tmdb = ''){
        $query = '';
        $cfile = OMEGADB_CACHE_DIR.'seasons.'.$tmdb;
        if(file_exists($cfile) && filemtime($cfile) + OMEGADB_CACHE_TIM >= time()){
            $query = file_get_contents($cfile);
            $query = maybe_unserialize($query);
        }else{
            $query = array(
                'post_type'      => 'seasons',
                'post_status'    => 'publish',
                'posts_per_page' => 1000,
                'paged'          => 1,
                'meta_query' => array(
                    array(
                        'key'   => 'ids',
                        'value' => $tmdb
                    )
                ),
                'meta_key' => 'temporada',
                'orderby'  => 'meta_value_num',
                'order'    => self::st_get_option('orderseasons','ASC')
            );
            $query = new WP_Query($query);
            $query = wp_list_pluck($query->posts,'ID');
            file_put_contents($cfile,serialize($query));
        }
        return apply_filters('omegadb_get_static_seasons', $query, $tmdb);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function GetAllEpisodes($tmdb = '', $season = ''){
        $query = '';
        $cfile = OMEGADB_CACHE_DIR.'episodes_'.$season.'.'.$tmdb;
        if(file_exists($cfile) && filemtime($cfile) + OMEGADB_CACHE_TIM >= time()){
            $query = file_get_contents($cfile);
            $query = maybe_unserialize($query);
        }else{
            $query = array(
                'post_type'      => 'episodes',
                'post_status'    => 'publish',
                'posts_per_page' => 1000,
                'paged'          => 1,
                'meta_query' => array(
                    array(
                        'key'   => 'ids',
                        'value' => $tmdb
                    ),
                    array(
                        'key' => 'temporada',
                        'value' => $season
                    )
                ),
                'meta_key' => 'episodio',
                'orderby'  => 'meta_value_num',
                'order'    => self::st_get_option('orderepisodes','ASC')
            );
            $query = new WP_Query($query);
            $query = wp_list_pluck($query->posts,'ID');
            file_put_contents($cfile,serialize($query));
        }
        return apply_filters('omegadb_get_static_episodes', $query, $tmdb.$season);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function EpisodeNav($tmdb ='', $season ='', $episode =''){
        return array(
            'prev' => self::EpisodeData($tmdb, $season, $episode-1),
            'next' => self::EpisodeData($tmdb, $season, $episode+1),
            'tvsh' => self::TVShowData($tmdb)
        );
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function EpisodeData($tmdb ='', $season ='', $episode =''){
        $query = array(
            'post_type'      => 'episodes',
            'post_status'    => 'publish',
            'meta_query' => array(
                array(
                    'key'   => 'ids',
                    'value' => $tmdb
                ),
                array(
                    'key' => 'temporada',
                    'value' => $season,
                ),
                array(
                    'key' => 'episodio',
                    'value' => $episode,
                )
            ),
            'paged' => 1,
            'posts_per_page' => 1,
        );
        $query = new WP_Query($query);
        $query = wp_list_pluck($query->posts,'ID');
        if(isset($query[0])){
            return array(
                'title'     => get_the_title($query[0]),
                'permalink' => get_permalink($query[0])
            );
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function SeasonData($tmdb ='', $season =''){
        $query = array(
            'post_type'      => 'seasons',
            'post_status'    => 'publish',
            'meta_query' => array(
                array(
                    'key'   => 'ids',
                    'value' => $tmdb
                ),
                array(
                    'key' => 'temporada',
                    'value' => $season,
                )
            ),
            'paged' => 1,
            'posts_per_page' => 1,
        );
        $query = new WP_Query($query);
        $query = wp_list_pluck($query->posts,'ID');
        if(isset($query[0])){
            return array(
                'title'     => get_the_title($query[0]),
                'permalink' => get_permalink($query[0])
            );
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function TVShowData($tmdb = ''){
        $query = array(
            'post_type'   => 'tvshows',
            'post_status' => 'publish',
            'meta_query' => array(
                array(
                    'key'   => 'ids',
                    'value' => $tmdb
                )
            ),
            'paged' => 1,
            'posts_per_page' => 1,
        );
        $query = new WP_Query($query);
        $query = wp_list_pluck($query->posts,'ID');
        if(isset($query[0])){
            return array(
                'post_id'   => $query[0],
                'title'     => get_the_title($query[0]),
                'permalink' => get_permalink($query[0]),
                'editlink'  => admin_url('post.php?action=edit&post='.$query[0])
            );
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function GetIMDbID($page = 1, $per_page = 5){
        $imdb  = array();
        $query = array(
            'post_type' => 'movies',
            'post_status' => 'publish',
            'paged' => $page,
            'posts_per_page' => $per_page,
        );
        $query = new WP_Query($query);
        $query = wp_list_pluck($query->posts,'ID');
        if($query){
            self::UpdateIMDbSett($page);
            foreach($query as $id){
                $meta = get_post_meta($id,'ids',true);
                $imdb[] = self::UpdateIMDb($meta,$id);
            }
        }else{
            update_option(OMEGADB_OPTIMDB, array('time' => time(), 'page' => '1'));
        }
        return $imdb;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function UpdateIMDb($imdb = '', $post_id = ''){
        // Response
        $response = array();
        // Omgdb Api Key
        $dbmv = self::st_get_option('omegadb');
        // IMDb Parameters
        $imdb_args = array(
            'key'  => $dbmv,
            'id' => $imdb
        );
        // Json Remote
        $json_imdb = self::SRemoteJson($imdb_args,OMEGADB_DBMVAPI);
        // Verify Response
        if(isset($json_imdb['response']) && $json_imdb['response'] == true){
            // Cache
            $cache = new ZetaFlixCache;
            // Get Rating
            $imdb_countr = isset($json_imdb['country']) ? $json_imdb['country'] : false;
            $imdb_rated  = isset($json_imdb['rated']) ? $json_imdb['rated'] : false;
			$imdb_rating = isset($json_imdb['imdb_rating']) ? $json_imdb['imdb_rating'] : false;
            $imdb_votes  = isset($json_imdb['imdb_votes']) ? $json_imdb['imdb_votes'] : false;

            // Update Options
            if($imdb_rating) update_post_meta($post_id, 'imdbRating', sanitize_text_field($imdb_rating));
            if($imdb_votes) update_post_meta($post_id, 'imdbVotes', sanitize_text_field($imdb_votes));
            if($imdb_rated) update_post_meta($post_id, 'Rated', sanitize_text_field($imdb_rated));
            if($imdb_countr) update_post_meta($post_id, 'Country', sanitize_text_field($imdb_countr));
            // Delete Cache
            $cache->delete($post_id.'_postmeta');
            // Response
            $response = array(
                'imdb'   => $imdb,
                'rating' => $imdb_rating,
                'votes'  => $imdb_votes,
                'title'  => get_the_title($post_id),
                'plink'  => get_permalink($post_id),
                'elink'  => admin_url('post.php?post='.$post_id.'&action=edit')
            );
        }else{
            $response = array(
                'imdb' => false,
                'message' => isset($json_imdb['error']) ? $json_imdb['error'] : __z('Pending process')
            );
        }
        // Response
        return $response;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function UpdateIMDbSett($page = ''){
        $option = get_option(OMEGADB_OPTIMDB);
        $optime = isset($option['time']) ? $option['time'] : time();
        $oppage = isset($option['page']) ? $option['page'] : '1';
        $sepage = !empty($page) ? ($page+1) : $oppage;
        if(($optime+172800) >= time()){
            $new_data = array(
                'time' => time(),
                'page' => $sepage
            );
        }else{
            $new_data = array(
                'time' => time(),
                'page' => '1'
            );
        }
        if($page){
            update_option(OMEGADB_OPTIMDB,$new_data);
        } else{
            return $new_data['page'];
        }
    }
	

	
	public function get_country_tv($abbreviation = ''){
		$countries = array(
			'AD' => array( 'name' => 'Andorra'),
			'UE' => array( 'name' => 'United Arab Emirates'),
			'AF' => array( 'name' => 'Afghanistan'),
			'AL' => array( 'name' => 'Albania'),
			'AM' => array( 'name' => 'Armenia'),
			'AN' => array( 'name' => 'Netherlands Antilles'),
			'AO' => array( 'name' => 'Angola'),
			'AQ' => array( 'name' => 'Antarctica'),
			'AR' => array( 'name' => 'Argentina'),
			'AS' => array( 'name' => 'American Samoa'),
			'AT' => array( 'name' => 'Austria'),
			'AU' => array( 'name' => 'Australia'),
			'AW' => array( 'name' => 'Aruba'),
			'AZ' => array( 'name' => 'Azerbaijan'),
			'BA' => array( 'name' => 'Bosnia and Herzegovina'),
			'BB' => array( 'name' => 'Barbados'),
			'BD' => array( 'name' => 'Bangladesh'),
			'BE' => array( 'name' => 'Belgium'),
			'BF' => array( 'name' => 'Burkina Faso'),
			'BG' => array( 'name' => 'Bulgaria'),
			'BH' => array( 'name' => 'Bahrain'),
			'BI' => array( 'name' => 'Burundi'),
			'BJ' => array( 'name' => 'Benin'),
			'BM' => array( 'name' => 'Bermuda'),
			'BN' => array( 'name' => 'Brunei Darussalam'),
			'BO' => array( 'name' => 'Bolivia'),
			'BR' => array( 'name' => 'Brazil'),
			'BS' => array( 'name' => 'Bahamas'),
			'BT' => array( 'name' => 'Bhutan'),
			'BV' => array( 'name' => 'Bouvet Island'),
			'BW' => array( 'name' => 'Botswana'),
			'BY' => array( 'name' => 'Belarus'),
			'BZ' => array( 'name' => 'Belize'),
			'CA' => array( 'name' => 'Canada'),
			'CC' => array( 'name' => 'Cocos Islands'),
			'CF' => array( 'name' => 'Central African Republic'),
			'CH' => array( 'name' => 'Switzerland'),
			'CI' => array( 'name' => 'Cote D Ivoire'),
			'CK' => array( 'name' => 'Cook Islands'),
			'CL' => array( 'name' => 'Chile'),
			'CM' => array( 'name' => 'Cameroon'),
			'CN' => array( 'name' => 'China'),
			'CO' => array( 'name' => 'Colombia'),
			'CR' => array( 'name' => 'Costa Rica'),
			'CS' => array( 'name' => 'Serbia and Montenegro'),
			'CU' => array( 'name' => 'Cuba'),
			'CV' => array( 'name' => 'Cape Verde'),
			'CX' => array( 'name' => 'Christmas Island'),
			'CY' => array( 'name' => 'Cyprus'),
			'CZ' => array( 'name' => 'Czech Republic'),
			'DE' => array( 'name' => 'Germany'),
			'DJ' => array( 'name' => 'Djibouti'),   
			'DK' => array( 'name' => 'Denmark'),   
			'DM' => array( 'name' => 'Dominica'),   
			'DO' => array( 'name' => 'Dominican Republic'),   
			'DZ' => array( 'name' => 'Algeria'),   
			'EC' => array( 'name' => 'Ecuador'),   
			'EE' => array( 'name' => 'Estonia'),   
			'EG' => array( 'name' => 'Egypt'),   
			'EH' => array( 'name' => 'Western Sahara'),   
			'ER' => array( 'name' => 'Eritrea'),   
			'ES' => array( 'name' => 'Spain'),   
			'ET' => array( 'name' => 'Ethiopia'),   
			'FI' => array( 'name' => 'Finland'),   
			'FJ' => array( 'name' => 'Fiji'),   
			'FK' => array( 'name' => 'Falkland Islands'),   
			'FM' => array( 'name' => 'Micronesia'),   
			'FO' => array( 'name' => 'Faeroe Islands'),   
			'FR' => array( 'name' => 'France'),   
			'GA' => array( 'name' => 'Gabon'),   
			'GB' => array( 'name' => 'United Kingdom'),   
			'GD' => array( 'name' => 'Grenada'),   
			'GE' => array( 'name' => 'Georgia'),   
			'GF' => array( 'name' => 'French Guiana'),   
			'GH' => array( 'name' => 'Ghana'),   
			'GI' => array( 'name' => 'Gibraltar'),   
			'GL' => array( 'name' => 'Greenland'),   
			'GM' => array( 'name' => 'Gambia'),   
			'GN' => array( 'name' => 'Guinea'),   
			'GP' => array( 'name' => 'Guadaloupe'),   
			'GQ' => array( 'name' => 'Equatorial Guinea'),   
			'GR' => array( 'name' => 'Greece'),   
			'GT' => array( 'name' => 'Guatemala'),   
			'GU' => array( 'name' => 'Guam'),   
			'GW' => array( 'name' => 'Guinea-Bissau'),   
			'GY' => array( 'name' => 'Guyana'),   
			'HK' => array( 'name' => 'Hong Kong'),   
			'HN' => array( 'name' => 'Honduras'),   
			'HR' => array( 'name' => 'Croatia'),   
			'HT' => array( 'name' => 'Haiti'),   
			'HU' => array( 'name' => 'Hungary'),   
			'ID' => array( 'name' => 'Indonesia'),   
			'IE' => array( 'name' => 'Ireland'),   
			'IL' => array( 'name' => 'Israel'),   
			'IN' => array( 'name' => 'India'),   
			'IQ' => array( 'name' => 'Iraq'),   
			'IR' => array( 'name' => 'Iran'),   
			'IS' => array( 'name' => 'Iceland'),   
			'IT' => array( 'name' => 'Italy'),   
			'JM' => array( 'name' => 'Jamaica'),   
			'JO' => array( 'name' => 'Jordan'), 
			'JP' => array( 'name' => 'Japan'),   
			'KE' => array( 'name' => 'Kenya'),
			'KG' => array( 'name' => 'Kyrgyz Republic'),   
			'KH' => array( 'name' => 'Cambodia'),
			'KI' => array( 'name' => 'Kiribati'),
			'KM' => array( 'name' => 'Comoros'),
			'KP' => array( 'name' => 'North Korea'),   
			'KR' => array( 'name' => 'South Korea'),   
			'KW' => array( 'name' => 'Kuwait'),   
			'KY' => array( 'name' => 'Cayman Islands'),   
			'KZ' => array( 'name' => 'Kzakhstan'),   
			'LA' => array( 'name' => 'Laos'),   
			'LB' => array( 'name' => 'Lebanon'),  
			'LC' => array( 'name' => 'St. Lucia'),  
			'LI' => array( 'name' => 'Liechtenstein'),  
			'LK' => array( 'name' => 'Sri Lanka'),  
			'LR' => array( 'name' => 'Liberia'),  
			'LS' => array( 'name' => 'Lesotho'),  
			'LT' => array( 'name' => 'Lithuania'),  
			'LU' => array( 'name' => 'Luxembourg'),  
			'LV' => array( 'name' => 'Latvia'),  
			'LY' => array( 'name' => 'Libya'),  
			'MA' => array( 'name' => 'Morocco'),  
			'ME' => array( 'name' => 'Montenegro'),  
			'MG' => array( 'name' => 'Madagascar'),  
			'MH' => array( 'name' => 'Marshall Islands'),  
			'MK' => array( 'name' => 'Macedonia'),  
			'ML' => array( 'name' => 'Mali'),  
			'MM' => array( 'name' => 'Myanmar'),  
			'MN' => array( 'name' => 'Mongolia'),  
			'MO' => array( 'name' => 'Macao'),  
			'MP' => array( 'name' => 'Northern Mariana Islands'),  
			'MQ' => array( 'name' => 'Martinique'),  
			'MR' => array( 'name' => 'Mauritania'),  
			'MS' => array( 'name' => 'Montserrat'),  
			'MT' => array( 'name' => 'Malta'),  
			'MU' => array( 'name' => 'Mauritius'),  
			'MV' => array( 'name' => 'Maldives'),  
			'MW' => array( 'name' => 'Malawi'),  
			'MX' => array( 'name' => 'Mexico'),  
			'MZ' => array( 'name' => 'Mozambique'),  
			'NA' => array( 'name' => 'Namibia'),  
			'NC' => array( 'name' => 'New Caledonia'),  
			'NE' => array( 'name' => 'Niger'),  
			'NF' => array( 'name' => 'Norfolk Island'),  
			'NG' => array( 'name' => 'Nigeria'),  
			'NI' => array( 'name' => 'Nicaragua'),  
			'NL' => array( 'name' => 'Netherlands'),  
			'NO' => array( 'name' => 'Norway'),  
			'NP' => array( 'name' => 'Nepal'), 
			'NR' => array( 'name' => 'Nauru'), 
			'NU' => array( 'name' => 'Niue'), 
			'NZ' => array( 'name' => 'New Zealand'), 
			'OM' => array( 'name' => 'Oman'), 
			'PA' => array( 'name' => 'Panama'), 
			'PE' => array( 'name' => 'Peru'), 
			'PF' => array( 'name' => 'French Polynesia'), 
			'PG' => array( 'name' => 'Papua New Guinea'), 
			'PH' => array( 'name' => 'Philippines'), 
			'PK' => array( 'name' => 'Pakistan'), 
			'PL' => array( 'name' => 'Poland'), 
			'PN' => array( 'name' => 'Pitcairn Island'), 
			'PR' => array( 'name' => 'Puerto Rico'), 
			'PS' => array( 'name' => 'Palestine'), 
			'PT' => array( 'name' => 'Portugal'), 
			'PW' => array( 'name' => 'Palau'), 
			'PY' => array( 'name' => 'Paraguay'), 
			'QA' => array( 'name' => 'Qatar'), 
			'RE' => array( 'name' => 'Reunion'), 
			'RO' => array( 'name' => 'Romania'), 
			'RS' => array( 'name' => 'Serbia'), 
			'RU' => array( 'name' => 'Russia'), 
			'RW' => array( 'name' => 'Rwanda'), 
			'SA' => array( 'name' => 'Saudi Arabia'), 
			'SB' => array( 'name' => 'Solomon Islands'), 
			'SC' => array( 'name' => 'Seychelles'), 
			'SD' => array( 'name' => 'Sudan'), 
			'SE' => array( 'name' => 'Sweden'), 
			'SG' => array( 'name' => 'Singapore'), 
			'SH' => array( 'name' => 'St. Helen'), 
			'SI' => array( 'name' => 'Slovenia'), 
			'SK' => array( 'name' => 'Slovakia'), 
			'SL' => array( 'name' => 'Sierra Leone'), 
			'SM' => array( 'name' => 'San Marino'), 
			'SN' => array( 'name' => 'Senegal'), 
			'SO' => array( 'name' => 'Somalia'), 
			'SR' => array( 'name' => 'Suriname'), 
			'SO' => array( 'name' => 'South Sudan'), 
			'SV' => array( 'name' => 'El Salvador'), 
			'SY' => array( 'name' => 'Syria'), 
			'SZ' => array( 'name' => 'Eswatini'), 
			'TD' => array( 'name' => 'Chad'), 
			'TG' => array( 'name' => 'Togo'), 
			'TH' => array( 'name' => 'Thailand'), 
			'TJ' => array( 'name' => 'Tajikistan'), 
			'TK' => array( 'name' => 'Tokelau'), 
			'TL' => array( 'name' => 'Timor-Leste'), 
			'NO' => array( 'name' => 'Norway'), 
			'TM' => array( 'name' => 'Turkmenistan'), 
			'TN' => array( 'name' => 'Tunisia'), 
			'TO' => array( 'name' => 'Tonga'), 
			'TR' => array( 'name' => 'Turkey'), 
			'TV' => array( 'name' => 'Tuvalu'), 
			'TW' => array( 'name' => 'Taiwan'), 
			'TZ' => array( 'name' => 'Tanzania'), 
			'UA' => array( 'name' => 'Ukraine'), 
			'US' => array( 'name' => 'United States'), 
			'UY' => array( 'name' => 'Uruguay'), 
			'UZ' => array( 'name' => 'Uzbekistan'), 
			'VE' => array( 'name' => 'Venezuela'), 
			'VG' => array( 'name' => 'British Virgin Islands'), 
			'VI' => array( 'name' => 'US Virgin Islands'), 
			'VN' => array( 'name' => 'Vietnam'), 
			'VU' => array( 'name' => 'Vanuatu'), 
			'WS' => array( 'name' => 'Samoa'), 
			'XC' => array( 'name' => 'Czechoslovakia'), 
			'XG' => array( 'name' => 'East Germany'), 
			'XK' => array( 'name' => 'Kosovo'), 
			'YE' => array( 'name' => 'Yemen'), 
			'YT' => array( 'name' => 'Mayotte'), 
			'YU' => array( 'name' => 'Yugoslavia'), 
			'ZA' => array( 'name' => 'South Africa'), 
			'ZM' => array( 'name' => 'Zambia'), 
			'ZW' => array( 'name' => 'Zimbabwe'),
			);    
 		
			
		$country = $countries[$abbreviation]['name'];   

		return $country;

	}
    
	
}
