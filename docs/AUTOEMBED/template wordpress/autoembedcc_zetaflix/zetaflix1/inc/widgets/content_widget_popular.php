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

class ZT_Widget_popular extends WP_Widget {

	function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('Sort content by genres') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_content_trending');
		parent::__construct('ztw_content_trending', __z('ZetaFlix - Stream Trending'), $widget_ops, $control_ops );
	}

	function widget( $args, $instance ) {
		extract( $args );
		if($instance):
		//Our variables from the widget settings.
		$zeta_genre  = get_option('zt_genre_slug','genre');
		$title        =  zeta_isset($instance,'title') ;
		$title      = apply_filters('widget_title', $title );
		$num        = ( zeta_isset($instance,'zt_nun') ) ? $instance['zt_nun'] : null;
		$tipo       = ( zeta_isset($instance,'zt_tipo') ) ? $instance['zt_tipo'] : null;
		$count      = ( zeta_isset($instance,'zt_count') ) ? $instance['zt_count'] : null;
		$count		= ( $count > 10 ) ? 10 : $count;
		$default 	= ( zeta_isset($instance,'zt_default') ) ? $instance['zt_default'] : null;
		$filter     = ( zeta_isset($instance,'zt_filter') ) ? 'filter' : 'false';
		
		$nonce = ($tipo == 'all') ? 'data-sec="'.wp_create_nonce('zt-top-stream-widget').'"' : null;
		$items = ($tipo == 'all') ? 'data-count="'.$count .'"' : null;

		echo $before_widget;

		if ( $title )
		echo $before_title . $title . $after_title;		
	
		$active_m = ($tipo == 'movies' OR $tipo == 'all' && $default == 'movies') ? ' active' : null;
		$active_t = ($tipo == 'tvshows' OR $tipo == 'all' && $default == 'tvshows') ? ' active' : null;

	    echo '<div class="sidebar-content">';
	    echo '<div class="top-listing">'; 
			echo '<ul class="top-list-nav">';
			if($tipo == 'movies' || $tipo == 'all'){
				echo '<li id="tab-movies" class="'.$active_m.'"><a data-top="movies" '.$nonce.' '.$items.'>'.__z('Movies').'</a></li>';
			}
			if($tipo == 'tvshows' || $tipo == 'all'){
				echo '<li id="tab-tvshows" class="'.$active_t.'"><a data-top="tvshows" '.$nonce.' '.$items.'>'.__z('TV Shows').'</a></li>';
			}
			echo '</ul>';
		echo '<div id="top-list-items">';
		if($tipo == 'movies' || $tipo == 'all'){
			
			echo '<ul class="top-list'.$active_m.'" id="top-movies">';
			if($tipo == 'all' && $default == 'movies' OR $tipo == 'movies'){
				$num = 1;
				$transient = get_transient('zetaflix_popular_widget_movies');
				if(false === $transient){
					$transient = new WP_Query( array('post_type' => 'movies', 'showposts' => $count ) );
					set_transient('zetaflix_popular_widget_movies', $transient, MINUTE_IN_SECONDS*60);
				}
				
				while ( $transient->have_posts() ) : $transient->the_post(); 
					get_template_part('inc/parts/item_widget_popular', null, array('num' => $num)); 
					$num++; 
				endwhile;				
			}
			echo '</ul>';
		}
		if($tipo == 'tvshows' OR $tipo == 'all'){
			
			echo '<ul class="top-list'.$active_t.'" id="top-tvshows">';
			if($tipo == 'all' && $default == 'tvshows' OR $tipo == 'tvshows'){
			
				$num = 1;
				$transient = get_transient('zetaflix_popular_widget_tvshows');
				if(false === $transient){
					$transient = new WP_Query( array('post_type' => 'tvshows', 'showposts' => $count ) );
					set_transient('zetaflix_popular_widget_tvshows', $transient, MINUTE_IN_SECONDS*60);
				}
				
				while ( $transient->have_posts() ) : $transient->the_post(); 
					get_template_part('inc/parts/item_widget_popular', null, array('num' => $num)); 
					$num++; 
					endwhile;
			}
			echo '</ul>';
		}
		echo '</div>';
		echo '</div>';
		echo '</div>';
		
		wp_reset_query();

		// End Query
		echo $after_widget;
		endif;
	}
	//Update the widget
	function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		//Strip tags from title and name to remove HTML
		$instance['title'] = strip_tags( $new_instance['title'] );
		$instance['zt_tipo'] = strip_tags( $new_instance['zt_tipo'] );
		$instance['zt_default'] = strip_tags( $new_instance['zt_default'] );
		$instance['zt_count'] = strip_tags( $new_instance['zt_count'] );
		//$instance['zt_filter'] = strip_tags( $new_instance['zt_filter'] );

		return $instance;
	}
	function form( $instance ) {
		//Set up some default widget settings.
		$defaults = array('title' => 'Popular',  'zt_tipo' => '', 'zt_filter' => 'filter');
		$instance = wp_parse_args( (array) $instance, $defaults ); ?>
		<p>
			<label for="<?php echo $this->get_field_id('title'); ?>"><?php _z('Title'); ?></label>
			<input id="<?php echo $this->get_field_id('title'); ?>" name="<?php echo $this->get_field_name('title'); ?>" value="<?php echo $instance['title']; ?>" style="width:100%;" />
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_tipo'); ?>"><?php _z('Content type'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_tipo'); ?>" name="<?php echo $this->get_field_name('zt_tipo'); ?>" style="width:100%;">
				<option <?php if ('all' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value="all"><?php _z('All'); ?></option>
				<option <?php if ('movies' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value="movies"><?php _z('Movies only'); ?></option>
				<option <?php if ('tvshows' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value="tvshows"><?php _z('TV Shows only'); ?></option>
            </select>
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_default'); ?>"><?php _z('Default Selected'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_default'); ?>" name="<?php echo $this->get_field_name('zt_default'); ?>" style="width:100%;">
				<option <?php if ('movies' == $instance['zt_default'] ) echo 'selected="selected"'; ?> value="movies"><?php _z('Movies'); ?></option>
				<option <?php if ('tvshows' == $instance['zt_default'] ) echo 'selected="selected"'; ?> value="tvshows"><?php _z('TV Shows'); ?></option>
            </select>
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_count'); ?>"><?php _z('Items'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_count'); ?>" name="<?php echo $this->get_field_name('zt_count'); ?>" style="width:100%;">
				<option <?php if ('5' == $instance['zt_count'] ) echo 'selected="selected"'; ?> value="5">5</option>
				<option <?php if ('6' == $instance['zt_count'] ) echo 'selected="selected"'; ?> value="6">6</option>
				<option <?php if ('7' == $instance['zt_count'] ) echo 'selected="selected"'; ?> value="7">7</option>
				<option <?php if ('8' == $instance['zt_count'] ) echo 'selected="selected"'; ?> value="8">8</option>
				<option <?php if ('9' == $instance['zt_count'] ) echo 'selected="selected"'; ?> value="9">9</option>
				<option <?php if ('10' == $instance['zt_count'] ) echo 'selected="selected"'; ?> value="10">10</option>
            </select>
		</p>
	<?php
	}

}
