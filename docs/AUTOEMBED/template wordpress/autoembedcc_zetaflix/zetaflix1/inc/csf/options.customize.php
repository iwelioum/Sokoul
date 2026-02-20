<?php

CSF::createSection(ZETA_OPTIONS,
    array(
        'id'    => 'customize',
        'icon'  => 'fas fa-paint-brush',
        'title' => __z('Customize')
    )
); 



// Define defaults colors
switch(zeta_get_option('style','default')) {
    case 'dark':
        $mcolor = '#408BEA';
        $bcolor = '#464e5a';
    break;
    case 'fusion':
        $mcolor = '#408BEA';
        $bcolor = '#9facc1';
    break;
    case 'default':
        $mcolor = '#408BEA';
        $bcolor = '#F5F7FA';
    break;
}

/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Customize'),
        'parent' => 'customize',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'full_width',
                'type'    => 'switcher',
                'title'   => __z('Full Width'),
                'subtitle'    => __z('Enable fluid full body width'),
                'default' => true
            ),

           array(
                'type'    => 'subheading',
                'content' => __z('Search bar')
            ),
            array(
                'id' => 'search_bar_style',
                'type' => 'radio',
                'title' => __z('Input Style'),
                'default' => 'fullw',
                'options' => array(
                    'fixed' => __z('Fixed'),
                    'fullw'  => __z('Fullwidth')
                )
            ),
            array(
                'id' => 'search_style',
                'type' => 'radio',
                'title' => __z('Results Style'),
                'default' => 'default',
                'options' => array(
                    'default' => __z('Default'),
                    'v2'  => __z('v2')
                )
            ),
           array(
                'type'    => 'subheading',
                'content' => __z('Sidebar')
            ),
            array(
                'id'      => 'sidebar_display',
                'type'    => 'switcher',
                'title'   => __z('Display Sidebar'),
                'subtitle'    => __z('Check whether to activate or deactivate'),
                'default' => false
            ),
            array(
                'id'    => 'sidebar_location',
                'type'  => 'checkbox',
                'title' => __z('Location'),
                'subtitle'  => __z('Select the pages to display the sidebar'),
				'dependency' => array('sidebar_display', '==', 'true'),
                'options' => array(
                    'home' => __z('Home'),
                    'archive' => __z('Stream Archives'),
                    'movies' => __z('Stream Movies'),
					'tvshows' => __z('Stream TVShows'),
					'seasons' => __z('Stream Seasons'),
					'episodes' => __z('Stream Episodes'),
                    'blog_archive' => __z('Blog Archive'),
					'post' => __z('Blog Single'),
                ),
                'default' => array('home','archive','movies','tvshows','seasons','episodes','blog_archive', 'blog_single')
            ),
            array(
                'id'    => 'sidebar_position',
                'type'  => 'radio',
                'title' => __z('Position'),
                'subtitle'  => __z('Select the spot to display the sidebar'),
				'dependency' => array('sidebar_display', '==', 'true'),
                'options' => array(
                    'right' => __z('Right'),
                    'left' => __z('Left')
                ),
                'default' => 'right'
            ),
            array(
                'id'    => 'sidebar_scroll',
                'type'  => 'radio',
                'title' => __z('Layout'),
                'subtitle'  => __z('Select how the sidebar is shown'),
				'dependency' => array('sidebar_display', '==', 'true'),
                'options' => array(
                    'fixed' => __z('Fixed'),
                    'scroll' => __z('Scrollable')
                ),
                'default' => 'scroll'
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Customize logos')
            ),
            array(
                'id'    => 'headlogo',
                'type'  => 'media',
                'title' => __z('Logo header'),
                'subtitle'  => __z('Upload your logo using the Upload Button')
            ),
            array(
                'id'    => 'favicon',
                'type'  => 'media',
                'title' => __z('Favicon'),
                'subtitle'  => __z('Upload a 16 x 16 px image that will represent your website\'s favicon')
            ),
            array(
                'id'    => 'touchlogo',
                'type'  => 'media',
                'title' => __z('Touch icon APP'),
                'subtitle'  => __z('Upload a 152 x 152 px image that will represent your Web APP')
            ),
            array(
                'id'    => 'adminloginlogo',
                'type'  => 'media',
                'title' => __z('Login / Register / WP-Admin'),
                'subtitle'  => __z('Upload your logo using the Upload Button')
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Footer settings')
            ),
            array(
                'id'      => 'footer',
                'type'    => 'radio',
                'title'   => __z('Footer'),
                'default' => 'simple',
                'options' => array(
                    'simple'   => __z('Simple'),
                    'complete' => __z('Complete')
                )
            ),
            array(
                'id'    => 'footercopyright',
                'type'  => 'text',
                'title' => __z('Footer copyright'),
                'subtitle'  => __z('Modify or remove copyright text')
            ),
            array(
                'id'    => 'logofooter',
                'type'  => 'media',
                'title' => __z('Logo footer'),
				'subtitle'  => __z('Recommended image size is 300 x 75'),
                'dependency' => array('footer', '==', 'complete')
            ),
            array(
                'id'    => 'footertext',
                'type'  => 'textarea',
                'title' => __z('Footer text'),
                'subtitle'  => __z('Text under footer logo'),
                'dependency' => array('footer', '==', 'complete')
            ),
			
            array(
                'id'    => 'footersocialfb',
                'type'  => 'text',
                'title' => __z('Facebook'),
                'dependency' => array('footer', '==', 'complete')
            ),
            array(
                'id'    => 'footersocialtw',
                'type'  => 'text',
                'title' => __z('Twitter'),
                'dependency' => array('footer', '==', 'complete')
            ),
            array(
                'id'    => 'footersocialig',
                'type'  => 'text',
                'title' => __z('Instagram'),
                'dependency' => array('footer', '==', 'complete')
            ),
            array(
                'id'    => 'footersocialyt',
                'type'  => 'text',
                'title' => __z('Youtube'),
                'dependency' => array('footer', '==', 'complete')
            ),
			
			array(
			  'id'     => 'footertags',
			  'type'   => 'repeater',
			  'title'  => 'Footer Tags',
			  'fields' => array(
				array(
				  'id'    => 'tagname',
				  'type'  => 'text',
				  'title' => 'Name',
				),
				array(
				  'id'    => 'tagurl',
				  'type'  => 'text',
				  'title' => 'Link'
				),
			  ),
			   'dependency' => array('footer', '==', 'complete')
			),	
            array(
                'id'    => 'footerc1',
                'type'  => 'text',
                'title' => __z('Title column 1'),
                'subtitle'  => __z('Footer menu'),
                'dependency' => array('footer', '==', 'complete')
            ),
            array(
                'id'    => 'footerc2',
                'type'  => 'text',
                'title' => __z('Title column 2'),
                'subtitle'  => __z('Footer menu'),
                'dependency' => array('footer', '==', 'complete')
            ),
            array(
                'id'    => 'footerc3',
                'type'  => 'text',
                'title' => __z('Title column 3'),
                'subtitle'  => __z('Footer menu'),
                'dependency' => array('footer', '==', 'complete')
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Custom CSS')
            ),
            array(
                'id'   => 'css',
                'type' => 'code_editor',
                'settings' => array(
                    'theme'  => 'mbo',
                    'mode'   => 'css',
                ),
                'after' => '<p>'.__z('Add only CSS code').'</p>'
            )
        )
    )
);
