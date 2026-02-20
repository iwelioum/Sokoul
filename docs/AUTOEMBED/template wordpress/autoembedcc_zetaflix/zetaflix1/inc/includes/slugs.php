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

class ZetaSlugs {
	public function __construct() {
		add_action('admin_init', array( $this, 'settingsInit'));
		add_action('admin_init', array( $this, 'settingsSave'));
	}

	/* Fields
	-------------------------------------------------------------------------------
	*/
	public function settingsInit() {
		$this->addField('', array($this, 'slug_title'), '');
		$this->addField('zt_requests_slug', array( $this, 'requests_slug'), __z('Requests') );
		$this->addField('zt_author_slug', array( $this, 'author_slug'), __z('User Profile') );
		$this->addField('zt_movies_slug', array( $this, 'movies_slug'), __z('Movies') );
		$this->addField('zt_tvshows_slug', array( $this, 'tvshows_slug'), __z('TVShows') );
		$this->addField('zt_seasons_slug', array( $this, 'seasons_slug'), __z('Seasons') );
		$this->addField('zt_episodes_slug', array( $this, 'episodes_slug'), __z('Episodes') );
		$this->addField('zt_links_slug', array( $this, 'links_slug'), __z('Links') );
		$this->addField('zt_genre_slug', array( $this, 'genre_slug'), __z('Genre') );
		$this->addField('zt_release_slug', array( $this, 'release_slug'), __z('Release') );
		$this->addField('zt_network_slug', array( $this, 'network_slug'), __z('Network') );
		$this->addField('zt_studio_slug', array( $this, 'studio_slug'), __z('Studio') );
		$this->addField('zt_cast_slug', array( $this, 'cast_slug'), __z('Cast') );
		$this->addField('zt_creator_slug', array( $this, 'creator_slug'), __z('Creator') );
		$this->addField('zt_director_slug', array( $this, 'director_slug'), __z('Director') );
		$this->addField('zt_quality_slug', array( $this, 'quality_slug'), __z('Quality') );
	}

	/* Callbacks
	-------------------------------------------------------------------------------
	*/
	public function slug_title() {
		echo '<h3 id="zetaflix-permalinks">'. __z('ZetaFlix: Permalink Settings') .'</h3>';
	}

	public function author_slug() {
		echo $this->input('zt_author_slug', 'user', '/nickname/');
	}

	public function requests_slug() {
		echo $this->input('zt_requests_slug', 'requests', '');
	}

	public function movies_slug() {
		echo $this->input('zt_movies_slug', 'movies', '/titanic/');
	}

	public function tvshows_slug() {
		echo $this->input('zt_tvshows_slug', 'tvshows', '/the-walking-dead/');
	}

	public function seasons_slug() {
		echo $this->input('zt_seasons_slug', 'seasons', '/the-walking-dead-season-1/');
	}

	public function episodes_slug() {
		echo $this->input('zt_episodes_slug', 'episodes', '/the-walking-dead-1x1/');
	}

	public function genre_slug() {
		echo $this->input('zt_genre_slug', 'genre', '/action/');
	}

	public function release_slug() {
		echo $this->input('zt_release_slug', 'release', '/2016/');
	}

	public function network_slug() {
		echo $this->input('zt_network_slug', 'network', '/amc/');
	}

	public function studio_slug() {
		echo $this->input('zt_studio_slug', 'studio', '/amc-studios/');
	}

	public function cast_slug() {
		echo $this->input('zt_cast_slug', 'cast', '/andrew-lincoln/');
	}

	public function creator_slug() {
		echo $this->input('zt_creator_slug', 'creator', '/frank-darabont/');
	}

	public function director_slug() {
		echo $this->input('zt_director_slug', 'director', '/james-cameron/');
	}

	public function links_slug() {
		echo $this->input('zt_links_slug', 'links', '/1588/');
	}

	public function quality_slug() {
		echo $this->input('zt_quality_slug', 'quality', '/1080p/');
	}

	/* Save settings
	-------------------------------------------------------------------------------
	*/
	public function settingsSave() {
		if ( ! is_admin() ) return;
		$this->saveField('zt_author_slug');
		$this->saveField('zt_movies_slug');
		$this->saveField('zt_requests_slug');
		$this->saveField('zt_tvshows_slug');
		$this->saveField('zt_seasons_slug');
		$this->saveField('zt_episodes_slug');
		$this->saveField('zt_genre_slug');
		$this->saveField('zt_release_slug');
		$this->saveField('zt_network_slug');
		$this->saveField('zt_studio_slug');
		$this->saveField('zt_protagonist_slug');
		$this->saveField('zt_cast_slug');
		$this->saveField('zt_gueststar_slug');
		$this->saveField('zt_creator_slug');
		$this->saveField('zt_director_slug');
		$this->saveField('zt_links_slug');
		$this->saveField('zt_quality_slug');
	}

	/*Helpers
	-------------------------------------------------------------------------------
	*/
	public function input( $option_name = '', $placeholder = '', $type = '' ) {
		$slug = get_option( $option_name );
		$value = ( isset( $slug ) ) ? esc_attr( $slug ) : '';
		$utype = ($type) ? '<code>'. $type .'</code>' : null;

		return '<code>'. home_url() .'/</code><input class="zt_permaliks_input" name="'. $option_name .'" type="text" class="regular-text code" value="'. $slug .'" placeholder="'. $placeholder .'" />'. $utype;
	}
	public function addField( $option_name, $callback, $title ){
		add_settings_field(
			$option_name, // id
			$title,       // setting title
			$callback,    // display callback
			'permalink',  // settings page
			'optional'    // settings section
		);
	}
	public function saveField( $option_name ){
		if ( isset( $_POST[$option_name] ) ) {
			$permalink_structure = sanitize_title( $_POST[$option_name] );
			$permalink_structure = untrailingslashit( $permalink_structure );

			update_option( $option_name, $permalink_structure );
		}
	}
}
new ZetaSlugs;
