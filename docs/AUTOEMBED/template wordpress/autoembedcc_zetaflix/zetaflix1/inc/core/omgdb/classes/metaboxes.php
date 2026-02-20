<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

class OmegadbMetaboxes extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        add_action('add_meta_boxes',array(&$this,'metaboxes'));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function metaboxes(){
        add_meta_box('zt_metabox',__z('Movie Info'),array(&$this,'meta_movies'),'movies','normal','high');
        add_meta_box('zt_metabox',__z('TVShow Info'),array(&$this,'meta_tvshows'),'tvshows','normal','high');
        add_meta_box('zt_metabox',__z('Season Info'),array(&$this,'meta_seasons'),'seasons','normal','high');
        add_meta_box('zt_metabox',__z('Episode Info'),array(&$this,'meta_episodes'),'episodes','normal','high');
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function meta_movies(){
        // Nonce security
	    wp_nonce_field('_movie_nonce', 'movie_nonce');
		// Metabox options
		$options = array(
	        array(
	            'id'            => 'ids',
				'id2'		    => null,
				'id3'		    => null,
	            'type'          => 'generator',
	            'style'         => 'style="background: #f7f7f7"',
	            'class'         => 'regular-text',
	            'placeholder'   => 'tt2911666',
	            'label'         => __z('Generate data'),
	            'desc'          => __z('Generate data from <strong>imdb.com</strong>'),
	            'fdesc'         => __z('E.g. http://www.imdb.com/title/<strong>tt2911666</strong>/'),
                'requireupdate' => true,
                'previewpost'   => false
	        ),
	       // array(
	       //     'id'     => 'zt_featured_post',
	       //     'type'   => 'checkbox',
	       //     'label'  => __z('Featured Title'),
	       //     'clabel' => __z('Do you want to mark this title as a featured item?')
	        //),			
	        array(
	            'type'    => 'heading',
	            'colspan' => 2,
	            'text'    => __z('Main Slider')
	        ),
	        array(
	            'id'     => 'zt_featured_slider',
	            'type'   => 'checkbox',
	            'label'  => __z('Main Slider'),
	            'clabel' => __z('Do you want to add this to the Main Slider?')
	        ),
	        array(
	            'id'    => 'zt_logo',
	            'type'  => 'upload_single',
	            'label' => __z('Slide Logo'),
	            'desc'  => __z('Add url image')
	        ),
	        array(
	            'id'    => 'zt_banner',
	            'type'  => 'upload_single',
	            'label' => __z('Slide Backdrop'),
	            'desc'  => __z('Add url image')
	        ),
	        array(
	            'type'    => 'heading',
	            'colspan' => 2,
	            'text'    => __z('Images and trailer')
	        ),
	        array(
	            'id'    => 'zt_poster',
	            'type'  => 'upload_single',
	            'label' => __z('Poster'),
	            'desc'  => __z('Add url image')
	        ),
	        array(
	            'id'      => 'zt_backdrop',
	            'type'    => 'upload_single',
	            'label'   => __z('Main Backdrop'),
	            'desc'    => __z('Add url image')
	        ),
	        array(
	            'id'     => 'imagenes',
	            'type'   => 'upload_multi',
	            'rows'   => 5,
	            'aid'    => 'up_images_images',
	            'label'  => __z('Backdrops'),
	            'desc'   => __z('Place each image url below another')
	        ),
	        array(
	            'id'    => 'youtube_id',
	            'type'  => 'text',
	            'class' => 'small-text',
	            'label' => __z('Video trailer'),
	            'desc'  => __z('Add id Youtube video'),
	            'fdesc' => '[id_video_youtube]',
				'double' => null,
	        ),
	        array(
	            'type'    => 'heading',
	            'colspan' => 2,
	            'text'    => __z('IMDb.com data')
	        ),
	        array(
	            'double' => true,
	            'id'     => 'imdbRating',
	            'id2'    => 'imdbVotes',
	            'type'   => 'text',
	            'label'  => __z('Rating IMDb'),
	            'desc'   => __z('Average / votes')
	        ),
	        array(
	            'id'    => 'Rated',
	            'type'  => 'text',
	            'class' => 'small-text',
				'double' => null,
				'fdesc'	=> null,
	            'label' => __z('Rated')
	        ),
	        array(
	            'id'    => 'Country',
	            'type'  => 'text',
	            'class' => 'small-text',
				'fdesc'	=> null,
				'desc'	=> null,
				'double' => null,
	            'label' => __z('Country')
	        ),
	        array(
	            'type'    => 'heading',
	            'colspan' => 2,
	            'text' => __z('Themoviedb.org data')
	        ),
	        array(
	            'id'    => 'idtmdb',
	            'type'  => 'text',
				'fdesc'	=> null,
				'desc'	=> null,
				'double' => null,
				'class' => 'regular-text',
	            'label' => __z('ID TMDb')
	        ),
	        array(
	            'id'    => 'original_title',
	            'type'  => 'text',
				'class' => 'regular-text',
				'fdesc'	=> null,
				'double' => null,
				'desc' => null,
	            'label' => __z('Original title')
	        ),
	        array(
	            'id'    => 'tagline',
	            'type'  => 'text',
	            'class' => 'small-text',
				'fdesc'	=> null,
				'double' => null,
				'desc' => null,
	            'label' => __z('Tag line')
	        ),
	        array(
	            'id'    => 'release_date',
	            'type'  => 'date',
	            'label' => __z('Release Date')
	        ),
	        array(
	            'double' => true,
	            'id'     => 'vote_average',
	            'id2'    => 'vote_count',
	            'type'   => 'text',
	            'label'  => __z('Rating TMDb'),
	            'desc'   => __z('Average / votes')
	        ),
	        array(
	            'id'    => 'runtime',
	            'type'  => 'text',
	            'class' => 'small-text',
	            'label' => __z('Runtime')
	        ),
	        array(
	            'id'    => 'zt_dir',
	            'type'  => 'text',
	            'label' => __z('Director'),
				'class'	=> 'regular-text',
	        ),
	        array(
	            'id' => 'zt_cast',
	            'type' => 'textarea',
	            'rows' => 5,
				'upload' => false,
	            'label' => __z('Cast')
	        ),
	    );
	    $this->ViewMeta($options);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function meta_tvshows(){
        // Nonce security
	    wp_nonce_field('_tvshows_nonce', 'tvshows_nonce');
		// Metabox options
	    $options = array(
	        array(
	            'id'            => 'ids',
	            'type'          => 'generator',
	            'style'         => 'style="background: #f7f7f7"',
	            'class'         => 'regular-text',
	            'placeholder'   => '1402',
	            'label'         => __z('Generate data'),
	            'desc'          => __z('Generate data from <strong>themoviedb.org</strong>'),
	            'fdesc'         => __z('E.g. https://www.themoviedb.org/tv/<strong>1402</strong>-the-walking-dead'),
                'requireupdate' => true,
                'previewpost'   => false
	        ),
	        array(
	            'id'     => 'clgnrt',
	            'type'   => 'checkbox',
	            'label'  => __z('Seasons control'),
	            'clabel' => __z('I have generated seasons or I will manually')
	        ),
	        //array(
	        //    'id'     => 'zt_featured_post',
	        //   'type'   => 'checkbox',
	        //    'label'  => __z('Featured Title'),
	        //    'clabel' => __z('Do you want to mark this title as a featured item?')
	       // ),
	        array(
	            'type'    => 'heading',
	            'colspan' => 2,
	            'text'    => __z('Main Slider')
	        ),
	        array(
	            'id'     => 'zt_featured_slider',
	            'type'   => 'checkbox',
	            'label'  => __z('Main Slider'),
	            'clabel' => __z('Do you want to add this to the Main Slider?')
	        ),
	        array(
	            'id'    => 'zt_logo',
	            'type'  => 'upload_single',
	            'label' => __z('Slide Logo'),
	            'desc'  => __z('Add url image')
	        ),
	        array(
	            'id'    => 'zt_banner',
	            'type'  => 'upload_single',
	            'label' => __z('Slide Backdrop'),
	            'desc'  => __z('Add url image')
	        ),
	        array(
	            'type'    => 'heading',
	            'colspan' => 2,
	            'text'    => __z('Images and trailer')
	        ),
	        array(
	            'id'    => 'zt_poster',
	            'type'  => 'upload_single',
	            'label' => __z('Poster'),
	            'desc'  => __z('Add url image')
	        ),
	        array(
	            'id'      => 'zt_backdrop',
	            'type'    => 'upload_single',
	            'label'   => __z('Main Backdrop'),
	            'desc'    => __z('Add url image')
	        ),
	        array(
	            'id'     => 'imagenes',
	            'type'   => 'upload_multi',
	            'rows'   => 5,
	            'aid'    => 'up_images_images',
	            'label'  => __z('Backdrops'),
	            'desc'   => __z('Place each image url below another')
	        ),
	        array(
	            'id'    => 'youtube_id',
	            'type'  => 'text',
	            'class' => 'small-text',
	            'label' => __z('Video trailer'),
	            'desc'  => __z('Add id Youtube video'),
	            'fdesc' => '[id_video_youtube]'
	        ),
	        array(
	            'type'    => 'heading',
	            'colspan' => 2,
	            'text'    => __z('More data')
	        ),
	        array(
	            'id'    => 'original_name',
	            'type'  => 'text',
	            'class' => 'small-text',
	            'label' => __z('Original Name')
	        ),
	        array(
	            'id'    => 'first_air_date',
	            'type'  => 'date',
	            'label' => __z('Firt air date')
	        ),
	        array(
	            'id'    => 'last_air_date',
	            'type'  => 'date',
	            'label' => __z('Last air date')
	        ),
	        array(
	            'double' => true,
	            'id'     => 'number_of_seasons',
	            'id2'    => 'number_of_episodes',
	            'type'   => 'text',
	            'label'  => __z('Content total posted'),
	            'desc'   => __z('Seasons / Episodes')
	        ),
	        array(
	            'double' => true,
	            'id'     => 'imdbRating',
	            'id2'    => 'imdbVotes',
	            'type'   => 'text',
	            'label'  => __z('Rating TMDb'),
	            'desc'   => __z('Average / votes')
	        ),
	        array(
	            'id'    => 'episode_run_time',
	            'type'  => 'text',
	            'class' => 'small-text',
	            'label' => __z('Episode runtime')
	        ),
	        array(
	            'id'    => 'zt_creator',
	            'type'  => 'text',
				'class' => 'regular-text',
	            'label' => __z('Creator')
	        ),
	        array(
	            'id' => 'zt_cast',
	            'type' => 'textarea',
	            'rows' => 5,
	            'label' => __z('Cast')
	        ),
	    );
	    $this->ViewMeta($options);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function meta_seasons(){
        // Nonce security
	    wp_nonce_field('_seasons_nonce', 'seasons_nonce');
	    // Metabox options
	    $options = array(
	        array(
	            'id'           => 'ids',
	            'id2'          => 'temporada',
	            'type'         => 'generator',
	            'style'        => 'style="background: #f7f7f7"',
	            'class'        => 'extra-small-text',
	            'placeholder'  => '1402',
	            'placeholder2' => '1',
	            'label'        => __z('Generate data'),
	            'desc'         => __z('Generate data from <strong>themoviedb.org</strong>'),
	            'fdesc'        => __z('E.g. https://www.themoviedb.org/tv/<strong>1402</strong>-the-walking-dead/season/<strong>1</strong>/'),
                'requireupdate' => true,
                'previewpost'   => $this->get_option('nospostimp')
	        ),
	        array(
	            'id'     => 'clgnrt',
	            'type'   => 'checkbox',
	            'label'  => __z('Episodes control'),
	            'clabel' => __z('I generated episodes or add manually')
	        ),
	        array(
	            'id'    => 'serie',
	            'type'  => 'text',
	            'label' => __z('Serie name'),
				'class' => 'regular-text',
	        ),
	        array(
	            'id'    => 'zt_poster',
	            'type'  => 'upload_single',
	            'label' => __z('Poster'),
	            'desc'  => __z('Add url image'),
	        ),
	        array(
	            'id'      => 'zt_backdrop',
	            'type'    => 'upload_single',
	            'label'   => __z('Main Backdrop'),
	            'desc'    => __z('Add url image')
	        ),
	        array(
	            'id'     => 'imagenes',
	            'type'   => 'upload_multi',
	            'rows'   => 5,
	            'aid'    => 'up_images_images',
	            'label'  => __z('Backdrops'),
	            'desc'   => __z('Place each image url below another'),
				'class' => 'regular-text',
	        ),
	        array(
	            'id'    => 'youtube_id',
	            'type'  => 'text',
	            'class' => 'small-text',
	            'label' => __z('Video trailer'),
	            'desc'  => __z('Add id Youtube video'),
	            'fdesc' => '[id_video_youtube]'
	        ),
	        array(
	            'id'    => 'air_date',
	            'type'  => 'date',
	            'label' => __z('Air date')
	        )
	    );
	    $this->ViewMeta($options);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function meta_episodes(){
        // Nonce security
	    wp_nonce_field('_episodios_nonce','episodios_nonce');
	    // Metabox options
	    $options = array(
	        array(
	            'id'           => 'ids',
	            'id2'          => 'temporada',
	            'id3'          => 'episodio',
	            'type'         => 'generator',
	            'style'        => 'style="background: #f7f7f7"',
	            'class'        => 'extra-small-text',
	            'placeholder'  => '1402',
	            'placeholder2' => '1',
	            'placeholder3' => '2',
	            'label'        => __z('Generate data'),
	            'desc'         => __z('Generate data from <strong>themoviedb.org</strong>'),
	            'fdesc'        => __z('E.g. https://www.themoviedb.org/tv/<strong>1402</strong>-the-walking-dead/season/<strong>1</strong>/episode/<strong>2</strong>'),
                'requireupdate' => true,
                'previewpost'   => $this->get_option('nospostimp')
	        ),
	        array(
	            'id'    => 'episode_name',
	            'type'  => 'text',
	            'label' => __z('Episode title'),
				'class' => 'regular-text',
	        ),
	        array(
	            'id'    => 'serie',
	            'type'  => 'text',
	            'label' => __z('Serie name'),
				'class' => 'regular-text',
	        ),			
			
	        array(
	            'id'    => 'zt_poster',
	            'type'  => 'upload_single',
	            'label' => __z('Poster'),
	            'desc'  => __z('Add url image')
	        ),
	        array(
	            'id'      => 'zt_backdrop',
	            'type'    => 'upload_single',
	            'label'   => __z('Main Backdrop'),
	            'desc'    => __z('Add url image')
	        ),
	        array(
	            'id'     => 'imagenes',
	            'type'   => 'upload_multi',
	            'rows'   => 5,
	            'aid'    => 'up_images_images',
	            'label'  => __z('Backdrops'),
	            'desc'   => __z('Place each image url below another')
	        ),
	        array(
	            'id'    => 'youtube_id',
	            'type'  => 'text',
	            'class' => 'small-text',
	            'label' => __z('Video trailer'),
	            'desc'  => __z('Add id Youtube video'),
	            'fdesc' => '[id_video_youtube]'
	        ),
	        array(
	            'id'    => 'air_date',
	            'type'  => 'date',
	            'label' => __z('Air date')
	        )
	    );
	    $this->ViewMeta($options);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function ViewMeta($options){
        echo '<div id="loading_api"></div>';
	    echo '<div id="api_table"><table class="options-table-responsive zt-options-table"><tbody>';
		new Zetafields($options);
	    echo '</tbody></table></div>';
    }
}

new OmegadbMetaboxes;
