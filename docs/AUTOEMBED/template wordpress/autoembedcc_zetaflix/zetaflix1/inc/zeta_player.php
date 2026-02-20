<?php
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

class ZetaPlayer{
	// Attributes
	public $postmeta;

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public function __construct(){

        // Main postmeta
        $this->postmeta = 'repeatable_fields';

        // Actions
        add_action('save_post', array($this,'save'));
        add_action('admin_init', array($this,'add_metabox'), 1);

        // Ajax Actions
        add_action('wp_ajax_zeta_player_ajax', array($this,'ajax'));
    	add_action('wp_ajax_nopriv_zeta_player_ajax', array($this,'ajax'));

		// Api Rest
		add_action('rest_api_init', array($this,'api_route'));
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public function languages(){
		return array(
			__z('---------')			=> null,
			__z('Chinese')				=> 'cn',
			__z('Denmark')				=> 'dk',
			__z('Dutch')				=> 'nl',
			__z('English')				=> 'en',
			__z('English British')		=> 'gb',
			__z('Egypt')				=> 'egt',
			__z('French')				=> 'fr',
			__z('German')				=> 'de',
			__z('Indonesian')			=> 'id',
			__z('Hindi')				=> 'in',
			__z('Italian')				=> 'it',
			__z('Japanese')				=> 'jp',
			__z('Korean')				=> 'kr',
			__z('Philippines')			=> 'ph',
			__z('Portuguese Portugal')	=> 'pt',
			__z('Portuguese Brazil')	=> 'br',
			__z('Polish')				=> 'pl',
			__z('Romanian')				=> 'td',
			__z('Scotland')				=> 'sco',
			__z('Spanish Spain')		=> 'es',
			__z('Spanish Mexico')		=> 'mx',
			__z('Spanish Argentina')	=> 'ar',
			__z('Spanish Peru')			=> 'pe',
			__z('Spanish Chile')		=> 'cl',
			__z('Spanish Colombia')		=> 'co',
			__z('Sweden')				=> 'se',
			__z('Turkish')				=> 'tr',
			__z('Rusian')				=> 'ru',
			__z('Vietnam')				=> 'vn'
		);
	}

	/**
     * @since 1.0.0
     * @version 1.0
     */
	public function types_player_options(){
		// Normal types
		$types['iframe']   = __z('URL Embed');
		$types['mp4']      = __z('URL MP4');
		//$types['gdrive']   = __z('ID or URL Google Drive');
		// Special types
		//if(!zeta_get_option('playajax'))
		$types['ztshcode'] = __z('Shortcode or HTML');
		// Return Types
		return $types;
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public function type_player(){
		return array(
			__z('URL Iframe')			  => 'iframe',
			__z('URL MP4')				  => 'mp4',
			__z('ID or URL Google Drive') => 'gdrive',
			__z('Shortcode or HTML')	  => 'ztshcode',
		);
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public function add_metabox(){
		add_meta_box('repeatable-fields', __z('Video Player'), array($this,'view_metabox'), 'movies', 'normal', 'default');
		add_meta_box('repeatable-fields', __z('Video Player'), array($this,'view_metabox'), 'episodes', 'normal', 'default');
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public function view_metabox(){
        global $post;
		$postmneta = get_post_meta($post->ID, $this->postmeta, true);
		wp_nonce_field('zeta_player_editor_nonce','zeta_player_editor_nonce');
        require get_parent_theme_file_path('/inc/parts/player_editor.php');
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public function save($post_id){
		if(!isset($_POST['zeta_player_editor_nonce']) || !wp_verify_nonce($_POST['zeta_player_editor_nonce'], 'zeta_player_editor_nonce')) return;
		if(defined('DOING_AUTOSAVE') && DOING_AUTOSAVE) return;
		if(!current_user_can('edit_post',$post_id)) return;
		// Meta data
		$antiguo = get_post_meta($post_id, $this->postmeta, true);
		$nuevo	 = array();
		$options = $this->type_player();
		$names	 = zeta_isset($_POST,'name');
		$selects = zeta_isset($_POST,'select');
		$idiomas = zeta_isset($_POST,'idioma');
		$urls	 = zeta_isset($_POST,'url');
		$count	 = count($names);
		// Serialized data
		for($i = 0; $i < $count; $i++){
			if ($names[$i] != ''):
				$nuevo[$i]['name'] = stripslashes(strip_tags($names[$i]));
				if(in_array($selects[$i], $options)) $nuevo[$i]['select'] = $selects[$i];
				else $nuevo[$i]['select'] = '';
				if(in_array($idiomas[$i], $idiomas)) $nuevo[$i]['idioma'] = $idiomas[$i];
				else $nuevo[$i]['idioma'] = '';
				if($urls[$i] == 'http://') $nuevo[$i]['url'] = '';
				else $nuevo[$i]['url'] = stripslashes($urls[$i]);
			endif;
		}
		if(!empty($nuevo) && $nuevo != $antiguo) update_post_meta($post_id, $this->postmeta, $nuevo);
		elseif (empty($nuevo) && $antiguo) delete_post_meta($post_id, $this->postmeta, $antiguo);
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public function ajax(){
		// Set URL IFRAME
		$url_iframe = '';
		$url_playselect = '';
		
		// POST Data
        $post_id = zeta_isset($_POST,'post');
        $post_ty = zeta_isset($_POST,'type');
        $play_nm = zeta_isset($_POST,'nume');
		
		// Verify data
        if($post_id && ($play_nm > 0 OR $play_nm == 'trailer')){
			
			$url_playselect = get_permalink($post_id).'watch/'.$play_nm;
            // Get post meta
            switch ($post_ty) {
                case 'mv':
                    $postmeta = zeta_postmeta_movies($post_id);
                    break;
                case 'tv':
				case 'ss':
				case 'ep':
                    $postmeta = zeta_postmeta_episodes($post_id);
                    break;
            }
            // Compose Player
            $player = zeta_isset($postmeta,'players');
            $player = maybe_unserialize($player);
            // compose data
            $pag = zeta_compose_pagelink('jwpage');
            $url = ($play_nm != 'trailer') ? $this->ajax_isset($player, ($play_nm-1),'url') : false;
            $typ = ($play_nm == 'trailer') ? 'trailer' : $this->ajax_isset($player, ($play_nm-1),'select');
            // verify data
            if($typ){
                switch($typ){
					case 'iframe':
						$url_iframe = '<iframe class="metaframe rptss" src="' . $url. '" frameborder="0" scrolling="no" allow="autoplay; encrypted-media" allowfullscreen></iframe></div>';
						break;	
					case 'mp4':
					case 'gdrive':
						$url_iframe = "{$pag}?source=".urlencode($url)."&id={$post_id}&type={$typ}";
						break;
					case 'ztshcode':
						$url_iframe = do_shortcode($url);
						break;
					case 'trailer':
						$url_iframe = zeta_trailer_iframe_url_embed(zeta_isset($postmeta,'youtube_id'), 1);
						break;
                }
            }
        }
        // End Action
        wp_send_json(array('embed_url' => $url_iframe,'type' => $typ, 'play_url' => $url_playselect));
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public function ajax_isset($data = array(), $n = '', $k = ''){
		return (isset($data[$n][$k])) ? $data[$n][$k] : false;
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function viewer($post, $type, $players, $trailer, $size, $views, $ads = false, $image = false, $tvid = '', $ssep = ''){
		
		$nosplash = ' display-showvid';
		$playvid = false;
		// Set Ajax Player
		$splash = zeta_get_option('splashscreen');
		//$watchsplash = zeta_get_option('watch_splash');
		$watchsplash = true;
		$play_trailer = zeta_get_option('playtrailer');
		$play_fake = zeta_get_option('playfake');
		$force_play = (isset($play_trailer) OR isset($play_fake)) ? true : null;

		
		$watch_location = zeta_get_option('watch_location', 'same');
		$ajax_player = zeta_get_option('playajax');
		$play_pager  = zeta_compose_pagelink('jwpage');
		$source_name = zeta_get_option('playsource');
		$class_size  = ($size == 'regular') ? '' : ' bigger';
		$set_mode    = ($ajax_player == true) ? 'ajax_mode' : 'no_ajax';
		$current_page = ($tvid) ? get_permalink($tvid)."season/".$ssep[0]."/episode/".$ssep[2]."/" : get_permalink($post);
		
		$selectvid = zeta_get_option('nosplash');
		$auto_selectvid = (isset($selectvid) &&  $selectvid  === 'vid') ? ' active' : null;
		$auto_selecttrl = (isset($selectvid) &&  $selectvid  === 'trailerp') ? ' active' : null;
		$auto_selectfke = (isset($ajax_player) != true && isset($selectvid) &&  $selectvid  === 'fakep') ? ' active' : null;
		
		
		if(isset($selectvid) && $selectvid === 'trailerp'){
			if( $type != 'tv' && $type != 'tvss' ) {
				if($type === 'mv' && $type === 'ep'){
					if(!isset($play_trailer) == true && !isset($trailer)){
						$auto_selectvid = ' active';
					}else{
						$auto_selectvid = null;
					}					
				}else{
					$auto_selectvid = ' active';
				}
			}else{
				$auto_selectvid = null;
			}			
		}else{
			$auto_selectvid = null;
		}
		
		


		
		
		
		//if($watchsplash != true){
		//
		//	if($type != 'tv' && $type != 'ss' && $type != 'tvep'){				
		//		if(empty($players) && !is_array($players) && !isset($play_trailer) && !isset($play_fake)){
		//			$nosplash = ' display-'.$type.' display-novid';
		//		}else{
		//			$nosplash = ' display-showvid';
		//		}
		//	}else{
		//		$nosplash = ' display-'.$type.' display-novid';
		//	}
		//}


		
		if(isset($trailer) && isset($play_trailer)){
			$play_trailer = true;
			$position = zeta_get_option('trailerposition', 'first');
			$trailtitl = zeta_get_option('trailertitle', __z('Trailer'));
			$trailsrc = zeta_get_option('trailersource', __z('Youtube'));
		}
		
		if(isset($play_fake)){
			$play_fake = true;
			$fake_position = zeta_get_option('fakeposition', 'first');
			$faketitl = zeta_get_option('faketitl', __z('Premium'));
			$fakesrc = zeta_get_option('fakesource', __z('HD Server'));
		}
		
		
		// Define size
		$watch = get_query_var('watch');
		if($type == 'tvep') {
			if($watch_location != 'same'){
				$watch = (int)$ssep[4];
			}else{
				$watch = true;
			}
		}else{ 
			$watch = $watch; 
		}
        if(isset($players) OR isset($force_play) == true or $type === 'tv'){

			$ulclass = 'options';
			$html ="<div class='player-display".$nosplash."'>";		

				
			if($watch_location === 'same') {
				$playvid = true;
			}else{ 			
				if(!empty($watch )) {
					$playvid = true;
				}else{
				 $playvid = false;
				}
			}
			
			
			
			if(isset($playvid) OR isset($force_play)){
				if(isset($ajax_player)){
					$fake_display = (isset($play_fake)) ? "<div class='display-fake' style='display: none;'>".self::fake($image, 'source-box', 'return', 'player', 'ajax')."</div>" : null;
					$html .= "<div class='display-video'></div>".$fake_display;
				}else{
					$html .= "<div id='display-noajax'>";
					
					if(isset($fake_position) &&  $fake_position === 'first' && isset($play_fake)){
						$html .= self::fake($image, 'source-box', 'return', 'player');
					}
					
					if(isset($position) && $position === 'first' && isset($play_trailer) && $type === 'mv') {
						$html .="<div id='source-player-trailer' class='source-box".$auto_selecttrl."'><div class='pframe'>".zeta_trailer_iframe($trailer)."</div></div>";
					}
					$num = 1;
					
					
					//$auto_selectvid = (isset($selectvid) &&  $selectvid  === 'vid' && $selectvid === 'trailerp') ? ' active' : null;	
					
					if(!empty($players) && is_array($players)){
						
						
						
						
						foreach($players as $play){
							// Set Source
							$source = zeta_isset($play,'url');
							// HTML Player
							$autoselect_vid = (isset($auto_selectvid) && $num === 1) ? $auto_selectvid : null;
							
							$html .="<div id='source-player-{$num}' class='source-box".$autoselect_vid."'>";
							switch (zeta_isset($play,'select')) {
								case 'mp4':
									$html .="<div class='pframe'><if"."rame class='metaframe rptss' src='{$play_pager}?source=".urlencode($source)."&id={$post}&type=mp4' frameborder='0' scrolling='no' allow='autoplay; encrypted-media' allowfullscreen></ifr"."ame></div>";
									break;
								case 'iframe':
									$html .="<div class='pframe'><if"."rame class='metaframe rptss' src='{$source}' frameborder='0' scrolling='no' allow='autoplay; encrypted-media' allowfullscreen></ifr"."ame></div>";
									break;	
								case 'superembed':
									$html .="<div class='pframe'><if"."rame class='metaframe rptss' src='{$source}' frameborder='0' scrolling='no' allow='autoplay; encrypted-media' allowfullscreen></ifr"."ame></div>";
									break;	
								case 'ztshcode':
									$html .= "<div class='pframe'>".do_shortcode($source)."</div>";
									break;
							}
							$html .= "</div>";
							$num++;
						}
					}
					
					if(isset($position) === 'last' && isset($play_trailer)) {
						$html .="<div id='source-player-trailer' class='source-box".$auto_selecttrl."'><div class='pframe'>".zeta_trailer_iframe($trailer)."</div></div>";
					}
					
					if(isset($fake_position) === 'last' && isset($play_fake)){
						$html .= self::fake($image, 'source-box', 'return', 'player');
					}
					$html .= "</div>";
				}
			}
			
			if(!empty($tvid)) {
				$postid = $tvid;
			}else{
				$postid = $post;
			}

			
			if($splash == 'fake' && $play_fake != true){
				if($type != 'tv' && $type != 'tvep'){
					$html .= self::fake($image, 'regular', 'return');
				}else{
					$html .= zeta_playsplash($postid, $type, $tvid, $watch_location);		
				}
			}else{
				if($watchsplash == true){
					$html .= zeta_playsplash($postid, $type, $tvid, $watch_location);		
				}
			}
			
			


			
			
			
            if(!empty($ads) && $ajax_player){	
				$splashads = ($watchsplash == true) ? "style='display: none;'" : null;
                $html .="<div class='display-ads' {$splashads}><div class='atlga'>{$ads}</div><a class='ads-close'>".__z("Close")."</a></div>";
            }			
			
			

            $html .="</div>";
			// QBTNS 
			$html .= zeta_playqbtn($post, $type, $players);

			
			
			$type = ($type == 'tvep') ? 'ep' : $type;
			if($type == 'ep' OR $type == 'tv') {
				$html .= "<div class='ajax-episode'>";
			}
			if($type != 'tv'){
			$hidenav = '';
			//$hidenav = ($watchsplash == true && ($type === 'mv' || $type === 'ep') ) ? 'style="display:none"' : null;
            $html .= "<div id='playeroptions' class='{$ulclass} player-options' {$hidenav}><ul id='playeroptionsul' class='{$set_mode} play-lists'>"; 
			if(isset($fake_position) && $fake_position === 'first' && isset($play_fake)){
					if($watch_location == 'same') {						
						$url = null;
						$class = "class='zetaflix_player_option".$auto_selectfke."'";						
					}else{
						if(empty($watch)) {
							$url = "href='".$current_page."watch/fake/'";
							$class = null;
						}else{
							$url = null;
							$class = "class='zetaflix_player_option".$auto_selectfke."'";
						}
					}
				
					$html .= "<li id='player-option-fake' {$class} data-post='{$post}' data-type='{$type}' data-nume='fake'><a {$url}>";				
					$html .= "<span class='play-list-ico'><i class='fas fa-play mr-2'></i></span><span class='play-list-opt'><span class='opt-titl'>".$faketitl."</span><span class='opt-name'>".$fakesrc."</span></span>";
					$html .= "<span class='loader'></span></a></li>";
			}
			
            if($trailer){
				
				$trailerid = str_replace(array('[',']'), array('',''), $trailer);
				$trailerid = esc_attr($trailerid);
				
					if($watch_location == 'same') {						
						$url = null;
						$class = "class='zetaflix_player_option".$auto_selecttrl."'";						
					}else{
						if(empty($watch)) {
							$url = "href='".$current_page."watch/trailer/'";
							$class = null;
						}else{
							$url = null;
							$class = "class='zetaflix_player_option".$auto_selecttrl."'";
						}
					}
										
										
	
				
				if(isset($position) && $position === 'first' && isset($play_trailer) == true){

					$html .= "<li id='player-option-trailer' {$class} data-post='{$post}' data-type='{$type}' data-nume='trailer'><a {$url}>";				
					$html .= "<span class='play-list-ico'><i class='fas fa-play mr-2'></i></span><span class='play-list-opt'><span class='opt-titl'>".$trailtitl."</span><span class='opt-name'>".$trailsrc."</span></span>";
					$html .= "<span class='loader'></span></a></li>";
				}
            }
			
			
            $num = 1;
            if(!empty($players) && is_array($players)){
                foreach($players as $play){			

					$autoselect_nav = (isset($auto_selectvid) && $num === 1) ? $auto_selectvid : null;

					if($watch_location == 'same') {						
						$url = null;
						$class = "class='zetaflix_player_option".$autoselect_nav."'";
						
					}else{
						if (!empty($watch)) {
							$url = null;
							$class = "class='zetaflix_player_option".$autoselect_nav."'";
						}else{
							$url = "href='".$current_page."watch/{$num}/'";
							$class = null;
						}
					}

					$server = (isset($source_name)) ? zeta_compose_servername($play['url'], $play['select']) : ( (!empty($play['name'])) ? $play['name'] : __z("Source")." ".$num );
					$html .="<li id='player-option-{$num}' {$class} data-type='{$type}' data-post='{$post}' data-nume='{$num}'><a {$url}>";
					
					$html .="<span class='play-list-ico'>
								<i class='fas fa-play mr-2'></i>
							</span>
							<span class='play-list-opt'>
								<span class='opt-titl'>".__z('Server')."</span>
								<span class='opt-name'>{$server}</span>
							</span>";
					if($source_name == true)
                    	//$html .="<span class='server'>".zeta_compose_servername($play['url'], $play['select'])."</span>";
                    if(!empty($play['idioma'])){
                        //$html .="<span class='flag'><img src='".ZETA_URI."/assets/img/flags/{$play['idioma']}.png'></span>";
                    }
                    $html .="<span class='loader'></span></a></li>";
                    $num++;
					}
            }
			
			
            if(isset($position) && $position === 'last'  && isset($play_trailer) && $type === 'mv'){
				
					$html .="<li id='player-option-trailer' {$class} data-post='{$post}' data-type='{$type}' data-nume='trailer'><a {$url}>";				
					$html .="<span class='play-list-ico'><i class='fas fa-play mr-2'></i></span><span class='play-list-opt'><span class='opt-titl'>".$trailtitl."</span><span class='opt-name'>".$trailsrc."</span></span>";
					$html .="<span class='loader'></span></a></li>";

            }
			
			if(isset($fake_position) && $fake_position === 'last' && isset($play_fake)){
					$html .= "<li id='player-option-fake' {$class} data-post='{$post}' data-type='{$type}' data-nume='fake'><a {$url}>";				
					$html .= "<span class='play-list-ico'><i class='fas fa-play mr-2'></i></span><span class='play-list-opt'><span class='opt-titl'>".$faketitl."</span><span class='opt-name'>".$fakesrc."</span></span>";
					$html .= "<span class='loader'></span></a></li>";
			}
			
			}
			if($type != 'tv'){ 
			$html .= "</div>";
			$html .= "</ul>";
			}
			if($type == 'ep' OR $type == 'tv') {
			$html .= "</div>";
			}
            echo $html;
        }
    }
	
	
	
	
	public static function viewer_ss($postid, $tvid = '', $ids = '', $season = '', $episode = '', $location = 'same', $players = '', $trailer = '') {
		$players = (!empty($players)) ? true : false;
		$trailer = (!empty($trailer)) ? true : false;

		$noplayer = true;
		$ssep = $episode;
		
		if($episode){
			
			$episode = explode('/', $episode);
			$ep = (int)$episode[0];
			$watchv = $episode[1];
			$watch = (int)$episode[2];

			if($ep && $ids && $season){
				
					$args = array(
					'fields' => 'ids', 'post_type' => 'episodes', 'meta_query' => array( 'relation' => 'AND', array( 'key' => 'ids', 'value' => $ids, 'compare' => '=', ), array( 'key' => 'temporada', 'value' => $season, 'compare' => '=', ), array( 'key' => 'episodio', 'value' => $ep, 'compare' => '=', ), ) );	
					$episode = get_posts($args);
					if($episode){

						$epid = $episode[0];
						$epmeta = zeta_postmeta_episodes($episode[0]);
						$player = zeta_isset($epmeta,'players');
						$player  = maybe_unserialize($player);
						$trailer = zeta_isset($epmeta,'youtube_id');	
						
						if(!empty($player) && is_array($player)){		
						

							ZetaPlayer::viewer($epid, 'tvep', $player, $trailer, $player_wht, $tviews, $player_ads, $dynamicbg, $tvid, $episode); 
							$noplayer = false;
						}			
					}			
			}

		  
			
		}
		if($noplayer == true) zeta_noplayer($postid, 'ss', $tvid, $players, $trailer, $ssep);			
		
	}
	
    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function viewer_big($size, $ads = false, $image = false){
        if($size === 'bigger'){
            self::fake($image, 'bigger');
            $html ="<div class='zetaflix_player'>";
            $html .="<div id='zetaflix_player_big_content'></div>";
            $html .="</div>";
            echo $html;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function fake($image, $class = 'regular', $type = 'echo', $display = '', $ajax = ''){
		$selectvid = zeta_get_option('nosplash');
		$auto_selectfke = (isset($ajaxz) != true && isset($selectvid) &&  $selectvid  === 'fakep') ? ' active' : null;
		$playfake = zeta_get_option('playfake');
        $autolo = zeta_get_option('playautoload');
        $active = zeta_get_option('splashscreen');
        $pimage = isset($image) ? $image : zeta_get_option('fakebackdrop');
        $flinks = self::fake_links();
		
		
		$html = "";
		$id = ($playfake != true) ? "id='clickfakeplayer'" : null;

		$ajax = ($ajax == 'ajax') ? "style='display:none;'" : null;

        if($autolo != true && $active == 'fake' OR $playfake){
			
			
			$id = ($display == 'player') ? "id='source-player-fake'" : 'id="fakeplayer"';
			
            $html = "<div {$id} class='{$class} fakeplayer".$auto_selectfke."' ".$ajax.">";
            $html .="<a ".$id." rel='nofollow' href='{$flinks}' target='_blank'>";
            $html .="<div class='playbox'>";
            if(zeta_is_true('fakeoptions','qua')) $html .="<span class='quality'>HD</span>";
            if(zeta_is_true('fakeoptions','pla')) $html .="<span class='playbtn'><img src='".ZETA_URI."/assets/img/play.svg'/></span>";
            if($pimage) $html .="<img class='cover' src='{$pimage}'/>";
            $html .="<section>";
            $html .="<div class='progressbar'></div>";
            $html .="<div class='controls'><div class='box'>";
            $html .="<i class='fas fa-play-circle'></i>";
            if(zeta_is_true('fakeoptions','ads')) $html .="<i class='fas fa-dollar-sign flashit'></i> <small>".__z('Advertisement')."</small>";
            $html .="<i class='fas fa-expand right'></i>";
            $html .="<i class='fas fa-lightbulb right'></i>";
            $html .="</div></div></section>";
            $html .="</div></a></div>";
			
        }
		
		// Compose Fake Player
		if($type == 'return'){
			return $html;
		}else{
			echo $html;
		}		
		
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private static function fake_links(){
        $flinks = zeta_get_option('fakeplayerlinks');
        if(!empty($flinks) && is_array($flinks)){
            $numb = array_rand($flinks);
            $link = $flinks[$numb]['link'];
            return esc_url($link);
        } else {
            return false;
        }
    }

	/**
     * @since 1.0.0
     * @version 1.0
     */
	public function api_route(){
		register_rest_route('zetaplayer/v2','/(?P<id>\d+)/(?P<type>[a-zA-Z0-9-]+)/(?P<source>[a-zA-Z0-9-]+)',array(
			'methods'  			  => WP_REST_Server::READABLE,
			'callback'            => array($this,'api_action'),
			'permission_callback' => '__return_true',
		));
		register_rest_route('zetaplayer/v2','/(?P<type>[a-zA-Z0-9-]+)/(?P<id>\d+)',array(
			'methods'  			  => WP_REST_Server::READABLE,
			'callback'            => array($this,'api_action'),
			'permission_callback' => '__return_true',
		));
	}

	/**
     * @since 1.0.0
     * @version 1.0
     */
	public function api_action($data){
		// Verify Method
		if(zeta_get_option('playajaxmethod') !== 'wp_json' && zeta_get_option('playajaxmethodep') !== 'wp_json') return null;

		$url_playselect = '';


		// Compose Data
		$post_id   = zeta_isset($data,'id');
		$post_type = zeta_isset($data,'type');
		$post_numb = zeta_isset($data,'source');
		$post_numb = ($post_numb) ? $post_numb : 1;
		
		// Switching post_type
		switch ($post_type) {
			case 'mv':
				$postmeta = zeta_postmeta_movies($post_id);
				break;
			case 'tv':
				$postmeta = zeta_postmeta_episodes($post_id);
				break;
			case 'ss':
				$postmeta = zeta_postmeta_episodes($post_id);
			case 'ep':
				$postmeta = zeta_postmeta_episodes($post_id);
				break;
		}
		
		
		$default = '';
		$url_playselect = '';
		$status = 0;
		$error = __z('No video available.');
		$title = __z('Server');
		
		$set_mode = 'no_ajax';
		
		if($post_type == 'tvep' OR $post_type == 'ssep'){	
			
		
			// Compose Season Players
			if($post_id) {
				$ajax_player = zeta_get_option('playajax');
				$postmeta = zeta_postmeta_episodes($post_id);
				$trailer = zeta_isset($postmeta,'youtube_id');
				$player  = zeta_isset($postmeta,'players');
				$player  = maybe_unserialize($player);
				$set_mode    = ($ajax_player == true) ? 'ajax_mode' : 'no_ajax';
				
				$embeds = null;
				$navs = null;
				
					$num = 1;
					if(!empty($player) && is_array($player)){
						$error = '';
						$embeds = array();
						$navs = array();
						
						$vhtml = '';
						foreach($player as $play){
							$source = zeta_isset($play,'url');
							$type = zeta_isset($play,'select');

							
							switch($type) {
								case 'iframe':
									$video = $source;
									break;		
								case 'gdrive':
								case 'mp4':
									$video = "{$play_pager}?source=".urlencode($source)."&id={$post}&type={$type}";
									break;
								case 'ztshcode':
									$video = do_shortcode($source);
									break;
							}
							switch($set_mode){
								case 'ajax_mode': 
								$embeds[] = array('num' => $num, 'type' => $type, 'title' => $title,'name' => $play['name']);							
								break;
								case 'no_ajax':
								$embeds[] = array('num' => $num, 'code' => $video, 'type' => $type, 'title' => $title,'name' => $play['name']);							
								break;
							}
							$num++;					
							
							
						}
						$status = 1;
							
					}				
			}
			
		}else{		

			// Compose Player
			$player = zeta_isset($postmeta,'players');
			$player = maybe_unserialize($player);
			// Compose more data
			$pag = zeta_compose_pagelink('jwpage');
			
			if($post_type != 'tvep' && $post_type != 'ssep'){
				$url = ($post_numb != 'trailer') ? $this->ajax_isset($player, ($post_numb-1),'url') : false;
				$typ = ($post_numb == 'trailer') ? 'trailer' : $this->ajax_isset($player, ($post_numb-1),'select');
			}
			// Filter types
			switch($typ) {
				case 'iframe':
					$url_iframe = $url;
					break;	
				case 'mp4':
				case 'gdrive':
					$url_iframe = "{$pag}?source=".urlencode($url)."&id={$post_id}&type={$typ}";
					break;
				case 'ztshcode':
					$url_iframe = do_shortcode($url);
					break;
				case 'trailer':
					$url_iframe = zeta_trailer_iframe_url_embed(zeta_isset($postmeta,'youtube_id'), 1);
					break;
			}
			
		}
		
		// The Response
		
		if($post_type == 'tvep' or $post_type == 'ssep'){
			$response = array('pid' => $post_id, 'status' => $status, 'error' => $error, 'mode' => $set_mode, 'type' => $post_type, 'embed' => $embeds, 'default' => $default);
		}else{
			$response = array('embed_url' => $url_iframe, 'type' => $typ, 'play_url' => $url_playselect);
		}
		
		
		return $response;
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
	public function __destruct(){
		return false;
		}
			
}

new ZetaPlayer;